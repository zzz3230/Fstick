package routing

import (
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
		w.Header().Set("Access-Control-Allow-Headers", "*")
		w.Header().Set("Access-Control-Expose-Headers", "*")
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
	if strings.HasSuffix(target.Path, "/") && strings.HasPrefix(targetPath, "/") {
		target.Path = strings.TrimRight(target.Path, "/") + targetPath
	} else {
		target.Path = target.Path + targetPath
	}
	target.RawQuery = r.URL.RawQuery

	req, err := http.NewRequest(r.Method, target.String(), r.Body)
	if err != nil {
		logrus.WithError(err).Error("failed to build gateway proxy request")
		http.Error(w, "failed to proxy request", http.StatusInternalServerError)
		return
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

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		// Retry once — helps with transient Docker DNS failures on startup
		logrus.WithError(err).Warn("gateway proxy request failed, retrying once")
		time.Sleep(500 * time.Millisecond)
		req2, _ := http.NewRequest(r.Method, target.String(), nil)
		if req2 != nil {
			for name, vals := range req.Header {
				for _, val := range vals {
					req2.Header.Add(name, val)
				}
			}
			resp, err = http.DefaultClient.Do(req2)
		}
		if err != nil {
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

func ListPluginsProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins", userID)
	})
}

func GetPluginProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		pluginID := mux.Vars(r)["plugin_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins/"+url.PathEscape(pluginID), userID)
	})
}

func InitPluginUploadProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins", userID)
	})
}

func CommitPluginUploadProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		pluginID := mux.Vars(r)["plugin_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins/"+url.PathEscape(pluginID)+"/commit", userID)
	})
}

func GetPluginCodeClientProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		pluginID := mux.Vars(r)["plugin_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins/"+url.PathEscape(pluginID)+"/code/client", userID)
	})
}

func PluginCommandProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		pluginID := mux.Vars(r)["plugin_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins/"+url.PathEscape(pluginID)+"/command", userID)
	})
}

func PluginStateProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		pluginID := mux.Vars(r)["plugin_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins/"+url.PathEscape(pluginID)+"/state", userID)
	})
}

func InstallPluginProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations", userID)
	})
}

func ListInstallationsProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations", userID)
	})
}

func ConfirmInstallProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations/confirm", userID)
	})
}

func UninstallPluginProxy(cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		userID := authenticateAndGetUserID(w, r, uAPI)
		if userID == "" {
			return
		}
		installationID := mux.Vars(r)["installation_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations/"+url.PathEscape(installationID), userID)
	})
}

