package routing

import (
	"bytes"
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/gorilla/mux"

	"github.com/element-hq/dendrite/setup/config"
	userapi "github.com/element-hq/dendrite/userapi/api"
)

const testUserID = "@alice:localhost"

type fakeTokenAPI struct{}

func (fakeTokenAPI) QueryAccessToken(_ context.Context, req *userapi.QueryAccessTokenRequest, res *userapi.QueryAccessTokenResponse) error {
	if req.AccessToken == "good" {
		res.Device = &userapi.Device{UserID: testUserID}
	}
	return nil
}

type gatewayCall struct {
	method string
	path   string
	query  string
	userID string
	body   string
}

func newTestRouter(t *testing.T, gatewayURL string) *mux.Router {
	t.Helper()
	cfg := &config.Dendrite{}
	cfg.Fstick.GatewayURL = gatewayURL
	router := mux.NewRouter()
	registerProxyRoutes(router, cfg, fakeTokenAPI{})
	return router
}

func TestProxyRoutesForwardToGatewayPath(t *testing.T) {
	var got gatewayCall
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		got = gatewayCall{r.Method, r.URL.EscapedPath(), r.URL.RawQuery, r.Header.Get("X-User-Id"), string(body)}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer gateway.Close()
	router := newTestRouter(t, gateway.URL)

	tests := []struct {
		method  string
		path    string
		gateway string
	}{
		{"GET", "/api/v1/me", "/api/v1/me"},
		{"GET", "/api/v1/registry/plugins?owned=true", "/api/v1/plugins"},
		{"POST", "/api/v1/registry/plugins", "/api/v1/plugins"},
		{"GET", "/api/v1/registry/plugins/p1?chat_id=%21r%3Ax", "/api/v1/plugins/p1"},
		{"POST", "/api/v1/registry/plugins/p1/assets/commit", "/api/v1/plugins/p1/assets/commit"},
		{"GET", "/api/v1/registry/plugins/p1/code/client?branch_id=b1", "/api/v1/plugins/p1/code/client"},
		{"GET", "/api/v1/registry/plugins/p1/code/server?branch_id=b1", "/api/v1/plugins/p1/code/server"},
		{"GET", "/api/v1/registry/plugins/p1/branches/b1/edit", "/api/v1/plugins/p1/branches/b1/edit"},
		{"PUT", "/api/v1/registry/plugins/p1/branches/b1/code", "/api/v1/plugins/p1/branches/b1/code"},
		{"POST", "/api/v1/registry/plugins/p1/branches/b1/reload", "/api/v1/plugins/p1/branches/b1/reload"},
		{"POST", "/api/v1/registry/plugins/p1/publish", "/api/v1/plugins/p1/publish"},
		{"POST", "/api/v1/registry/plugins/p1/branches/b1/cancel", "/api/v1/plugins/p1/branches/b1/cancel"},
		{"POST", "/api/v1/registry/plugins/p1/branches/b1/claim", "/api/v1/plugins/p1/branches/b1/claim"},
		{"POST", "/api/v1/registry/plugins/p1/branches/b1/approve", "/api/v1/plugins/p1/branches/b1/approve"},
		{"POST", "/api/v1/registry/plugins/p1/branches/b1/reject", "/api/v1/plugins/p1/branches/b1/reject"},
		{"GET", "/api/v1/registry/moderation/branches?status=pending", "/api/v1/moderation/branches"},
		{"POST", "/api/v1/plugins/p1/command?chat_id=%21r%3Ax", "/api/v1/plugins/p1/command"},
		{"GET", "/api/v1/plugins/p1/state?chat_id=%21r%3Ax", "/api/v1/plugins/p1/state"},
		{"PUT", "/api/v1/plugins/p1/state?chat_id=%21r%3Ax", "/api/v1/plugins/p1/state"},
		{"POST", "/api/v1/installations?chat_id=%21r%3Ax", "/api/v1/installations"},
		{"GET", "/api/v1/installations?chat_id=%21r%3Ax", "/api/v1/installations"},
		{"POST", "/api/v1/installations/confirm", "/api/v1/installations/confirm"},
		{"PATCH", "/api/v1/installations/i1", "/api/v1/installations/i1"},
		{"DELETE", "/api/v1/installations/i1", "/api/v1/installations/i1"},
	}
	if len(tests) != len(proxyRoutes) {
		t.Fatalf("test covers %d routes, table has %d", len(tests), len(proxyRoutes))
	}

	for _, tc := range tests {
		t.Run(tc.method+" "+tc.path, func(t *testing.T) {
			got = gatewayCall{}
			req := httptest.NewRequest(tc.method, tc.path, strings.NewReader("payload"))
			req.Header.Set("Authorization", "Bearer good")
			req.Header.Set("X-User-Id", "@spoofed:localhost")
			rec := httptest.NewRecorder()

			router.ServeHTTP(rec, req)

			if rec.Code != http.StatusNoContent {
				t.Fatalf("status = %d, want 204", rec.Code)
			}
			wantQuery := ""
			if i := strings.Index(tc.path, "?"); i >= 0 {
				wantQuery = tc.path[i+1:]
			}
			if got.method != tc.method || got.path != tc.gateway || got.query != wantQuery {
				t.Errorf("gateway saw %s %s?%s, want %s %s?%s", got.method, got.path, got.query, tc.method, tc.gateway, wantQuery)
			}
			if got.userID != testUserID {
				t.Errorf("X-User-Id = %q, want %q", got.userID, testUserID)
			}
			if got.body != "payload" {
				t.Errorf("body = %q, want payload", got.body)
			}
		})
	}
}

func TestProxyRoutesEscapePathVariables(t *testing.T) {
	var gotPath string
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.EscapedPath()
		w.WriteHeader(http.StatusNoContent)
	}))
	defer gateway.Close()
	router := newTestRouter(t, gateway.URL)

	req := httptest.NewRequest("GET", "/api/v1/registry/plugins/a%20b", nil)
	req.Header.Set("Authorization", "Bearer good")
	router.ServeHTTP(httptest.NewRecorder(), req)

	if gotPath != "/api/v1/plugins/a%20b" {
		t.Errorf("gateway path = %q, want /api/v1/plugins/a%%20b", gotPath)
	}
}

func TestUnlistedRoutesAreNotProxied(t *testing.T) {
	var calls int32
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&calls, 1)
	}))
	defer gateway.Close()
	router := newTestRouter(t, gateway.URL)

	tests := []struct{ method, path string }{
		{"GET", "/api/v1/internal/installations/resolve"},
		{"POST", "/api/v1/registry/internal/branches"},
		{"GET", "/api/v1/registry/internal/plugins/p1"},
		{"POST", "/api/v1/registry/plugins/p1/commit"},
		{"DELETE", "/api/v1/registry/plugins/p1"},
		{"PUT", "/api/v1/registry/plugins/p1"},
	}
	for _, tc := range tests {
		req := httptest.NewRequest(tc.method, tc.path, nil)
		req.Header.Set("Authorization", "Bearer good")
		rec := httptest.NewRecorder()
		router.ServeHTTP(rec, req)
		if rec.Code != http.StatusNotFound && rec.Code != http.StatusMethodNotAllowed {
			t.Errorf("%s %s: status = %d, want 404/405", tc.method, tc.path, rec.Code)
		}
	}
	if calls != 0 {
		t.Errorf("gateway was called %d times", calls)
	}
}

func TestProxyRequiresValidToken(t *testing.T) {
	var calls int32
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&calls, 1)
	}))
	defer gateway.Close()
	router := newTestRouter(t, gateway.URL)

	for _, header := range []string{"", "Bearer bad"} {
		req := httptest.NewRequest("GET", "/api/v1/me", nil)
		if header != "" {
			req.Header.Set("Authorization", header)
		}
		rec := httptest.NewRecorder()
		router.ServeHTTP(rec, req)
		if rec.Code != http.StatusUnauthorized {
			t.Errorf("Authorization %q: status = %d, want 401", header, rec.Code)
		}
	}
	if calls != 0 {
		t.Errorf("gateway was called %d times", calls)
	}
}

func TestPreflightAllowsEditorRequests(t *testing.T) {
	router := newTestRouter(t, "http://gateway.invalid")
	req := httptest.NewRequest("OPTIONS", "/api/v1/registry/plugins/p1/branches/b1/code", nil)
	req.Header.Set("Origin", "http://localhost:8080")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want 204", rec.Code)
	}
	for header, want := range map[string]string{
		"Access-Control-Allow-Methods":  "PUT",
		"Access-Control-Allow-Headers":  "Content-Encoding",
		"Access-Control-Expose-Headers": "ETag",
	} {
		if !strings.Contains(rec.Header().Get(header), want) {
			t.Errorf("%s = %q, want to contain %q", header, rec.Header().Get(header), want)
		}
	}
	for _, want := range []string{"PATCH", "If-None-Match"} {
		all := rec.Header().Get("Access-Control-Allow-Methods") + rec.Header().Get("Access-Control-Allow-Headers")
		if !strings.Contains(all, want) {
			t.Errorf("preflight response missing %s", want)
		}
	}
}

func TestRetryResendsSameBody(t *testing.T) {
	previous := gatewayRetryDelay
	gatewayRetryDelay = time.Millisecond
	defer func() { gatewayRetryDelay = previous }()

	var attempts int32
	var bodies []string
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		bodies = append(bodies, string(body))
		if atomic.AddInt32(&attempts, 1) == 1 {
			hj, ok := w.(http.Hijacker)
			if !ok {
				t.Error("hijacking unsupported")
				return
			}
			conn, _, _ := hj.Hijack()
			conn.Close()
			return
		}
		w.WriteHeader(http.StatusCreated)
	}))
	defer gateway.Close()
	router := newTestRouter(t, gateway.URL)

	req := httptest.NewRequest("POST", "/api/v1/installations", bytes.NewReader([]byte(`{"a":1}`)))
	req.Header.Set("Authorization", "Bearer good")
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("status = %d, want 201", rec.Code)
	}
	if len(bodies) != 2 || bodies[0] != `{"a":1}` || bodies[1] != `{"a":1}` {
		t.Errorf("bodies = %q, want the same payload twice", bodies)
	}
}

func TestOversizedBodyIsRejectedBeforeProxying(t *testing.T) {
	var calls int32
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&calls, 1)
	}))
	defer gateway.Close()
	router := newTestRouter(t, gateway.URL)

	req := httptest.NewRequest("PUT", "/api/v1/registry/plugins/p1/branches/b1/code", bytes.NewReader(make([]byte, maxProxyBodyBytes)))
	req.Header.Set("Authorization", "Bearer good")
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, want 413", rec.Code)
	}
	if calls != 0 {
		t.Errorf("gateway was called %d times", calls)
	}
}

func TestResponseHeadersArePassedBack(t *testing.T) {
	gateway := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("If-None-Match") != `"v1"` {
			t.Errorf("If-None-Match = %q", r.Header.Get("If-None-Match"))
		}
		w.Header().Set("ETag", `"v1"`)
		w.WriteHeader(http.StatusNotModified)
	}))
	defer gateway.Close()
	router := newTestRouter(t, gateway.URL)

	req := httptest.NewRequest("GET", "/api/v1/registry/plugins/p1/code/client", nil)
	req.Header.Set("Authorization", "Bearer good")
	req.Header.Set("If-None-Match", `"v1"`)
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotModified || rec.Header().Get("ETag") != `"v1"` {
		t.Errorf("status = %d, etag = %q", rec.Code, rec.Header().Get("ETag"))
	}
}
