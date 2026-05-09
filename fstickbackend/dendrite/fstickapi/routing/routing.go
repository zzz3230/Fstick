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
		"/api/v1/chats/{chat_id}/messages",
		SendChatMessage(cfg, rsAPI, userAPI),
	).Methods(http.MethodPost, http.MethodOptions)

	router.Handle(
		"/api/v1/events/push",
		PushFstickEvent(fstickStore),
	).Methods(http.MethodPost, http.MethodOptions)
}
