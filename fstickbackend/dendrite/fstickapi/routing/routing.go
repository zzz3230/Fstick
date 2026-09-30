// Package routing provides HTTP handler registration for the Fstick internal API.
package routing

import (
	"net/http"

	"github.com/gorilla/mux"

	roomserverAPI "github.com/element-hq/dendrite/roomserver/api"
	"github.com/element-hq/dendrite/setup/config"
	"github.com/element-hq/dendrite/syncapi/streams"
	userapi "github.com/element-hq/dendrite/userapi/api"
)

type proxyRoute struct {
	method      string
	path        string
	gatewayPath string
}

// proxyRoutes lists the browser-facing routes proxied to the gateway.
// Service-only /internal paths must never be added here.
var proxyRoutes = []proxyRoute{
	{http.MethodGet, "/api/v1/me", "/api/v1/me"},

	{http.MethodGet, "/api/v1/registry/plugins", "/api/v1/plugins"},
	{http.MethodPost, "/api/v1/registry/plugins", "/api/v1/plugins"},
	{http.MethodGet, "/api/v1/registry/plugins/{plugin_id}", "/api/v1/plugins/{plugin_id}"},
	{http.MethodPost, "/api/v1/registry/plugins/{plugin_id}/assets/commit", "/api/v1/plugins/{plugin_id}/assets/commit"},
	{http.MethodGet, "/api/v1/registry/plugins/{plugin_id}/code/client", "/api/v1/plugins/{plugin_id}/code/client"},
	{http.MethodGet, "/api/v1/registry/plugins/{plugin_id}/code/server", "/api/v1/plugins/{plugin_id}/code/server"},
	{http.MethodGet, "/api/v1/registry/plugins/{plugin_id}/branches/{branch_id}/edit", "/api/v1/plugins/{plugin_id}/branches/{branch_id}/edit"},
	{http.MethodPut, "/api/v1/registry/plugins/{plugin_id}/branches/{branch_id}/code", "/api/v1/plugins/{plugin_id}/branches/{branch_id}/code"},
	{http.MethodPost, "/api/v1/registry/plugins/{plugin_id}/branches/{branch_id}/reload", "/api/v1/plugins/{plugin_id}/branches/{branch_id}/reload"},
	{http.MethodPost, "/api/v1/registry/plugins/{plugin_id}/publish", "/api/v1/plugins/{plugin_id}/publish"},
	{http.MethodPost, "/api/v1/registry/plugins/{plugin_id}/branches/{branch_id}/cancel", "/api/v1/plugins/{plugin_id}/branches/{branch_id}/cancel"},
	{http.MethodPost, "/api/v1/registry/plugins/{plugin_id}/branches/{branch_id}/claim", "/api/v1/plugins/{plugin_id}/branches/{branch_id}/claim"},
	{http.MethodPost, "/api/v1/registry/plugins/{plugin_id}/branches/{branch_id}/approve", "/api/v1/plugins/{plugin_id}/branches/{branch_id}/approve"},
	{http.MethodPost, "/api/v1/registry/plugins/{plugin_id}/branches/{branch_id}/reject", "/api/v1/plugins/{plugin_id}/branches/{branch_id}/reject"},
	{http.MethodGet, "/api/v1/registry/moderation/branches", "/api/v1/moderation/branches"},

	{http.MethodPost, "/api/v1/plugins/{plugin_id}/command", "/api/v1/plugins/{plugin_id}/command"},
	{http.MethodGet, "/api/v1/plugins/{plugin_id}/state", "/api/v1/plugins/{plugin_id}/state"},
	{http.MethodPut, "/api/v1/plugins/{plugin_id}/state", "/api/v1/plugins/{plugin_id}/state"},

	{http.MethodPost, "/api/v1/installations", "/api/v1/installations"},
	{http.MethodGet, "/api/v1/installations", "/api/v1/installations"},
	{http.MethodPost, "/api/v1/installations/confirm", "/api/v1/installations/confirm"},
	{http.MethodPatch, "/api/v1/installations/{installation_id}", "/api/v1/installations/{installation_id}"},
	{http.MethodDelete, "/api/v1/installations/{installation_id}", "/api/v1/installations/{installation_id}"},
}

// Setup registers all Fstick API routes on the provided router.
// The router is expected to be scoped to the /fstick/ path prefix.
//
// Routes registered:
//
//	GET  /api/v1/chats/{chat_id}/members/{user_id}
//	     Returns membership info for a user in a chat room.
//
//	POST /api/v1/chats/{chat_id}/messages
//	     Sends a message to a chat room on behalf of a plugin sender.
//
//	POST /api/v1/events/push
//	     Pushes a custom fstick event to a specific user's /sync stream.
func Setup(
	router *mux.Router,
	cfg *config.Dendrite,
	rsAPI roomserverAPI.ClientRoomserverAPI,
	userAPI userapi.ClientUserAPI,
	fstickStore *streams.FstickEventStore,
) {
	router.Handle(
		"/api/v1/chats/{chat_id}/members/{user_id}",
		GetChatMember(rsAPI),
	).Methods(http.MethodGet, http.MethodOptions)

	router.Handle(
		"/api/v1/chats/{chat_id}/members",
		GetChatMembers(rsAPI),
	).Methods(http.MethodGet, http.MethodOptions)

	router.Handle(
		"/api/v1/chats/{chat_id}/messages",
		SendChatMessage(cfg, rsAPI, userAPI),
	).Methods(http.MethodPost, http.MethodOptions)

	router.Handle(
		"/api/v1/events/push",
		PushFstickEvent(fstickStore),
	).Methods(http.MethodPost, http.MethodOptions)

	registerProxyRoutes(router, cfg, userAPI)
}

func registerProxyRoutes(router *mux.Router, cfg *config.Dendrite, uAPI userapi.QueryAcccessTokenAPI) {
	for _, route := range proxyRoutes {
		router.Handle(
			route.path,
			authProxy(cfg, uAPI, pathTemplate(route.gatewayPath)),
		).Methods(route.method, http.MethodOptions)
	}
}
