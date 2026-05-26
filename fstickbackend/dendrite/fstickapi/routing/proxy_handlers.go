package routing

import (
	"io"
	"net/http"
	"net/url"
	"strings"

	"github.com/gorilla/mux"
	"github.com/sirupsen/logrus"

	"github.com/element-hq/dendrite/setup/config"
)

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

func proxyToGateway(w http.ResponseWriter, r *http.Request, base *url.URL, targetPath string) {
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
		if strings.EqualFold(name, "Host") {
			continue
		}
		for _, val := range vals {
			req.Header.Add(name, val)
		}
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		logrus.WithError(err).Error("gateway proxy request failed")
		http.Error(w, "failed to proxy request", http.StatusBadGateway)
		return
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

func ListPluginsProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins")
	})
}

func GetPluginProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		pluginID := mux.Vars(r)["plugin_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins/"+url.PathEscape(pluginID))
	})
}

func InitPluginUploadProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins")
	})
}

func CommitPluginUploadProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		pluginID := mux.Vars(r)["plugin_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/plugins/"+url.PathEscape(pluginID)+"/commit")
	})
}

func InstallPluginProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations")
	})
}

func ListInstallationsProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations")
	})
}

func ConfirmInstallProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations/confirm")
	})
}

func UninstallPluginProxy(cfg *config.Dendrite) http.HandlerFunc {
	return corsMiddleware(func(w http.ResponseWriter, r *http.Request) {
		installationID := mux.Vars(r)["installation_id"]
		proxyToGateway(w, r, gatewayURL(cfg), "/api/v1/installations/"+url.PathEscape(installationID))
	})
}

