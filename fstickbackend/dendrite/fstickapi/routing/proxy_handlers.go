package routing

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/gorilla/mux"
	"github.com/sirupsen/logrus"

	"github.com/element-hq/dendrite/clientapi/auth"
	"github.com/element-hq/dendrite/setup/config"
	userapi "github.com/element-hq/dendrite/userapi/api"
)

// gatewayHTTPClient is a shared HTTP client with a generous timeout for gateway proxy calls.
var gatewayHTTPClient = &http.Client{
	Timeout: 30 * time.Second,
}

const maxProxyBodyBytes = 3 << 20

var gatewayRetryDelay = 500 * time.Millisecond

// corsMiddleware wraps an HTTP handler to add CORS headers to responses.
// This allows browser-based clients to make cross-origin requests to the fstick API.
func corsMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin != "" {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
		} else {
			w.Header().Set("Access-Control-Allow-Origin", "*")
		}
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD")
		w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, Content-Encoding, If-None-Match, Accept")
		w.Header().Set("Access-Control-Expose-Headers", "ETag, Cache-Control, Content-Type")
		w.Header().Set("Access-Control-Allow-Credentials", "true")
		w.Header().Set("Access-Control-Max-Age", "86400")

		// If this is a preflight request (OPTIONS), respond immediately.
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		// Otherwise, proceed with the actual handler.
		next(w, r)
	}
}

func gatewayURL(cfg *config.Dendrite) *url.URL {
	if cfg == nil || cfg.Fstick.GatewayURL == "" {
		return nil
	}
	u, err := url.Parse(cfg.Fstick.GatewayURL)
	if err != nil {
		logrus.WithError(err).Warn("invalid fstick.gateway_url")
		return nil
	}
	return u
}

// authenticateAndGetUserID validates the Matrix access token from the request
// and returns the full Matrix user ID (e.g. @alice:localhost).
// On failure it writes an error response and returns "".
func authenticateAndGetUserID(w http.ResponseWriter, r *http.Request, uAPI userapi.QueryAcccessTokenAPI) string {
	device, jsonErr := auth.VerifyUserFromRequest(r, uAPI)
	if jsonErr != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(jsonErr.Code)
		if data, err := json.Marshal(jsonErr.JSON); err == nil {
			_, _ = w.Write(data)
		}
		return ""
	}
	return device.UserID
}

func proxyToGateway(w http.ResponseWriter, r *http.Request, base *url.URL, targetPath string, userID string) {
	if base == nil {
		http.Error(w, "gateway-service is not configured", http.StatusServiceUnavailable)
		return
	}

	target := *base
	escapedPath := strings.TrimRight(base.EscapedPath(), "/") + targetPath
	decodedPath, err := url.PathUnescape(escapedPath)
	if err != nil {
		http.Error(w, "invalid request path", http.StatusBadRequest)
		return
	}
	target.Path = decodedPath
	target.RawPath = escapedPath
	target.RawQuery = r.URL.RawQuery

	var body []byte
	if r.Body != nil {
		body, err = io.ReadAll(io.LimitReader(r.Body, maxProxyBodyBytes))
		if err != nil {
			http.Error(w, "failed to read request body", http.StatusBadRequest)
			return
		}
		if len(body) >= maxProxyBodyBytes {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusRequestEntityTooLarge)
			_, _ = w.Write([]byte(`{"error":"body_too_large","message":"Request body is too large"}`))
			return
		}
	}

	newRequest := func() (*http.Request, error) {
		req, err := http.NewRequest(r.Method, target.String(), bytes.NewReader(body))
		if err != nil {
			return nil, err
		}
		for name, vals := range r.Header {
			if strings.EqualFold(name, "Host") || strings.EqualFold(name, "X-User-Id") {
				continue
			}
			for _, val := range vals {
				req.Header.Add(name, val)
			}
		}
		// Inject the authenticated user ID so backend services can trust it.
		if userID != "" {
			req.Header.Set("X-User-Id", userID)
		}
		return req, nil
	}

	req, err := newRequest()
	if err != nil {
		logrus.WithError(err).Error("failed to build gateway proxy request")
		http.Error(w, "failed to proxy request", http.StatusInternalServerError)
		return
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		// Retry once, helps with transient Docker DNS failures on startup
		logrus.WithError(err).Warn("gateway proxy request failed, retrying once")
		time.Sleep(gatewayRetryDelay)
		req2, buildErr := newRequest()
		if buildErr == nil {
			resp, err = http.DefaultClient.Do(req2)
		}
		if buildErr != nil || err != nil {
			logrus.WithError(err).Error("gateway proxy request failed after retry")
			http.Error(w, "failed to proxy request", http.StatusBadGateway)
			return
		}
	}
	defer resp.Body.Close()

	for name, vals := range resp.Header {
		if strings.HasPrefix(strings.ToLower(name), "access-control-") {
			continue
		}
		for _, val := range vals {
			w.Header().Add(name, val)
		}
	}
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}

// authProxy authenticates the caller and proxies the request to the gateway path
// produced by gatewayPath, keeping the query string.
func authProxy(
	cfg *config.Dendrite,
	uAPI userapi.QueryAcccessTokenAPI,
	gatewayPath func(vars map[string]string) string,
) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		proxyToGateway(w, r, gatewayURL(cfg), gatewayPath(mux.Vars(r)), userID)
	})
}

// pathTemplate expands {var} placeholders with path-escaped route variables.
func pathTemplate(template string) func(vars map[string]string) string {
	return func(vars map[string]string) string {
		path := template
		for name, value := range vars {
			path = strings.ReplaceAll(path, "{"+name+"}", url.PathEscape(value))
		}
		return path
	}
}
