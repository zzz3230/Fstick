// Package fstickapi provides internal HTTP endpoints for the Fstick plugin system.
// These endpoints are intended for internal microservice use only and should
// not be exposed to untrusted clients.
package fstickapi

import (
	"github.com/element-hq/dendrite/fstickapi/routing"
	"github.com/element-hq/dendrite/internal/httputil"
	roomserverAPI "github.com/element-hq/dendrite/roomserver/api"
	"github.com/element-hq/dendrite/setup/config"
	"github.com/element-hq/dendrite/syncapi/streams"
	userapi "github.com/element-hq/dendrite/userapi/api"
)

// AddRoutes registers all Fstick internal API routes on the FstickAPI router.
func AddRoutes(
	routers httputil.Routers,
	cfg *config.Dendrite,
	rsAPI roomserverAPI.ClientRoomserverAPI,
	userAPI userapi.ClientUserAPI,
	fstickStore *streams.FstickEventStore,
) {
	routing.Setup(routers.FstickAPI, cfg, rsAPI, userAPI, fstickStore)
}
