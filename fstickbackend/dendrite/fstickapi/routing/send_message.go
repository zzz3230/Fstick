package routing

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/gorilla/mux"
	"github.com/matrix-org/gomatrixserverlib"
	"github.com/matrix-org/gomatrixserverlib/spec"
	"github.com/matrix-org/gomatrixserverlib/tokens"
	"github.com/sirupsen/logrus"

	"github.com/element-hq/dendrite/internal/eventutil"
	roomserverAPI "github.com/element-hq/dendrite/roomserver/api"
	"github.com/element-hq/dendrite/roomserver/types"
	"github.com/element-hq/dendrite/setup/config"
	userapi "github.com/element-hq/dendrite/userapi/api"
)

// validPluginIDRegex only allows alphanumeric characters, hyphens, and underscores
// to prevent injection into Matrix user IDs.
var validPluginIDRegex = regexp.MustCompile(`^[a-zA-Z0-9\-_]{1,64}$`)

type sendMessageRequest struct {
	PluginSenderID string `json:"plugin_sender_id"`
	Message        string `json:"message"`
}

type sendMessageResponse struct {
	EventID string `json:"event_id"`
}

// SendChatMessage returns an HTTP handler that sends a Matrix message (m.room.message)
// to a room on behalf of a plugin.
//
// The plugin is identified by plugin_sender_id (a UUID or alphanumeric identifier).
// A dedicated Matrix user account is automatically provisioned for each plugin sender:
//
//	@fstick-plugin-<plugin_sender_id>:<server_name>
//
// If the plugin user is not yet a member of the target room it is automatically joined.
// For private rooms the handler first finds a local room member to invite the plugin user,
// then retries the join so that the invite-accept flow is respected.
//
// Request body: { "plugin_sender_id": "<id>", "message": "Hello world" }
// Response:     { "event_id": "$eventid" }
func SendChatMessage(
	cfg *config.Dendrite,
	rsAPI roomserverAPI.ClientRoomserverAPI,
	userAPI userapi.ClientUserAPI,
) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		vars := mux.Vars(r)
		chatID := vars["chat_id"]
		ctx := r.Context()

		var req sendMessageRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
			return
		}

		if req.PluginSenderID == "" || req.Message == "" {
			writeJSONError(w, http.StatusBadRequest, "plugin_sender_id and message are required")
			return
		}

		if !validPluginIDRegex.MatchString(req.PluginSenderID) {
			writeJSONError(w, http.StatusBadRequest, "invalid plugin_sender_id: only alphanumeric characters, hyphens and underscores are allowed (max 64 chars)")
			return
		}

		serverName := cfg.Global.ServerName
		pluginLocalpart := fmt.Sprintf("fstick-plugin-%s", req.PluginSenderID)
		pluginUserID := fmt.Sprintf("@%s:%s", pluginLocalpart, serverName)

		// Ensure plugin user account exists (idempotent).
		var accRes userapi.PerformAccountCreationResponse
		if err := userAPI.PerformAccountCreation(ctx, &userapi.PerformAccountCreationRequest{
			AccountType: userapi.AccountTypeUser,
			Localpart:   pluginLocalpart,
			ServerName:  serverName,
			OnConflict:  userapi.ConflictUpdate,
		}, &accRes); err != nil {
			logrus.WithError(err).WithField("plugin_sender_id", req.PluginSenderID).
				Error("fstickapi: failed to provision plugin user account")
			writeJSONError(w, http.StatusInternalServerError, "failed to provision plugin user account")
			return
		}

		// Get or create an access device for the plugin user.
		device, err := getOrCreatePluginDevice(ctx, cfg, userAPI, pluginLocalpart, pluginUserID)
		if err != nil {
			logrus.WithError(err).WithField("plugin_sender_id", req.PluginSenderID).
				Error("fstickapi: failed to get/create plugin device")
			writeJSONError(w, http.StatusInternalServerError, "failed to provision plugin device")
			return
		}

		// Ensure the plugin user is a member of the room.
		fullUserID, err := spec.NewUserID(pluginUserID, true)
		if err != nil {
			writeJSONError(w, http.StatusInternalServerError, "internal error constructing plugin user ID")
			return
		}

		membershipRes := &roomserverAPI.QueryMembershipForUserResponse{}
		if err := rsAPI.QueryMembershipForUser(ctx, &roomserverAPI.QueryMembershipForUserRequest{
			RoomID: chatID,
			UserID: *fullUserID,
		}, membershipRes); err != nil {
			logrus.WithError(err).Error("fstickapi: failed to query room membership")
			writeJSONError(w, http.StatusInternalServerError, "failed to check room membership")
			return
		}

		if !membershipRes.IsInRoom {
			if err := ensurePluginUserInRoom(ctx, cfg, rsAPI, chatID, pluginUserID, req.PluginSenderID); err != nil {
				logrus.WithError(err).WithFields(logrus.Fields{
					"plugin_sender_id": req.PluginSenderID,
					"room_id":          chatID,
				}).Error("fstickapi: plugin user could not join room")
				writeJSONError(w, http.StatusForbidden, "plugin user cannot join the room: "+err.Error())
				return
			}
		}

		// Build the message event.
		messageContent := map[string]interface{}{
			"msgtype":                 "m.text",
			"body":                    req.Message,
			"fstick.plugin_sender_id": req.PluginSenderID,
		}

		e, buildErr := buildMessageEvent(ctx, messageContent, device, chatID, rsAPI, time.Now())
		if buildErr != nil {
			logrus.WithError(buildErr).Error("fstickapi: failed to build message event")
			writeJSONError(w, http.StatusInternalServerError, "failed to build message event: "+buildErr.Error())
			return
		}

		// Send the event to the roomserver.
		if err := roomserverAPI.SendEvents(
			ctx, rsAPI,
			roomserverAPI.KindNew,
			[]*types.HeaderedEvent{{PDU: e}},
			spec.ServerName(device.UserDomain()),
			serverName,
			serverName,
			nil,
			false,
		); err != nil {
			logrus.WithError(err).Error("fstickapi: failed to send event to roomserver")
			writeJSONError(w, http.StatusInternalServerError, "failed to send message")
			return
		}

		logrus.WithFields(logrus.Fields{
			"event_id":         e.EventID(),
			"room_id":          chatID,
			"plugin_sender_id": req.PluginSenderID,
		}).Info("fstickapi: plugin message sent")

		writeJSON(w, http.StatusOK, sendMessageResponse{EventID: e.EventID()})
	})
}

// ensurePluginUserInRoom attempts to join the plugin user to the room.
// For private rooms (where a direct join is rejected) it first locates a local member
// of the room, sends an invite from that member, and then retries the join.
func ensurePluginUserInRoom(
	ctx context.Context,
	cfg *config.Dendrite,
	rsAPI roomserverAPI.ClientRoomserverAPI,
	chatID, pluginUserID, pluginSenderID string,
) error {
	displayName := fmt.Sprintf("Plugin %s", pluginSenderID)

	// First, try a direct join (works for public rooms).
	_, _, joinErr := rsAPI.PerformJoin(ctx, &roomserverAPI.PerformJoinRequest{
		RoomIDOrAlias: chatID,
		UserID:        pluginUserID,
		Content:       map[string]interface{}{"displayname": displayName},
	})
	if joinErr == nil {
		return nil // joined successfully
	}

	// If the join was rejected (private room, restricted room, etc.) attempt
	// to find a local member who can send an invite, then retry.
	if !strings.Contains(joinErr.Error(), roomserverAPI.InputWasRejected) {
		return joinErr // some other error, propagate it
	}

	logrus.WithFields(logrus.Fields{
		"room_id":          chatID,
		"plugin_sender_id": pluginSenderID,
	}).Info("fstickapi: direct join rejected, trying invite-then-join for private room")

	if err := autoInvitePluginUser(ctx, cfg, rsAPI, chatID, pluginUserID); err != nil {
		return fmt.Errorf("auto-invite failed: %w", err)
	}

	// Retry join – now a pending invite should allow it.
	if _, _, retryErr := rsAPI.PerformJoin(ctx, &roomserverAPI.PerformJoinRequest{
		RoomIDOrAlias: chatID,
		UserID:        pluginUserID,
		Content:       map[string]interface{}{"displayname": displayName},
	}); retryErr != nil {
		return fmt.Errorf("join after invite failed: %w", retryErr)
	}

	return nil
}

// autoInvitePluginUser finds a local member of the room (preferring admins) and
// uses them to send a Matrix invite to the plugin user.
func autoInvitePluginUser(
	ctx context.Context,
	cfg *config.Dendrite,
	rsAPI roomserverAPI.ClientRoomserverAPI,
	chatID, pluginUserID string,
) error {
	// Query all local joined members.
	membersRes := &roomserverAPI.QueryMembershipsForRoomResponse{}
	if err := rsAPI.QueryMembershipsForRoom(ctx, &roomserverAPI.QueryMembershipsForRoomRequest{
		JoinedOnly: true,
		LocalOnly:  true,
		RoomID:     chatID,
	}, membersRes); err != nil {
		return fmt.Errorf("QueryMembershipsForRoom: %w", err)
	}

	if len(membersRes.JoinEvents) == 0 {
		return fmt.Errorf("room has no local members – cannot auto-invite")
	}

	// Prefer an admin (power level >= 50); otherwise fall back to any local member.
	inviterUserID := findBestInviter(ctx, rsAPI, chatID, membersRes, cfg.Global.ServerName)
	if inviterUserID == "" {
		return fmt.Errorf("could not determine a suitable local inviter")
	}

	inviter, err := spec.NewUserID(inviterUserID, true)
	if err != nil {
		return fmt.Errorf("invalid inviter user ID %q: %w", inviterUserID, err)
	}
	invitee, err := spec.NewUserID(pluginUserID, true)
	if err != nil {
		return fmt.Errorf("invalid invitee user ID %q: %w", pluginUserID, err)
	}
	validRoomID, err := spec.NewRoomID(chatID)
	if err != nil {
		return fmt.Errorf("invalid room ID %q: %w", chatID, err)
	}

	identity, err := cfg.Global.SigningIdentityFor(inviter.Domain())
	if err != nil {
		return fmt.Errorf("SigningIdentityFor %s: %w", inviter.Domain(), err)
	}

	logrus.WithFields(logrus.Fields{
		"inviter": inviterUserID,
		"invitee": pluginUserID,
		"room_id": chatID,
	}).Info("fstickapi: sending auto-invite for plugin user")

	return rsAPI.PerformInvite(ctx, &roomserverAPI.PerformInviteRequest{
		InviteInput: roomserverAPI.InviteInput{
			RoomID:     *validRoomID,
			Inviter:    *inviter,
			Invitee:    *invitee,
			IsDirect:   false,
			KeyID:      identity.KeyID,
			PrivateKey: identity.PrivateKey,
			EventTime:  time.Now(),
		},
		InviteRoomState: nil,
		SendAsServer:    string(inviter.Domain()),
	})
}

// findBestInviter picks the best local member to use as an inviter.
// It prefers users with admin-level power (>= 50) so the invite is more likely to
// succeed in rooms with elevated invite power-level requirements.
// Falls back to the first local member if no admin is found.
func findBestInviter(
	ctx context.Context,
	rsAPI roomserverAPI.ClientRoomserverAPI,
	chatID string,
	membersRes *roomserverAPI.QueryMembershipsForRoomResponse,
	serverName spec.ServerName,
) string {
	// Collect all local member user IDs (Sender field in ClientEvent is the Matrix user ID).
	var localMembers []string
	for _, ev := range membersRes.JoinEvents {
		if ev.Sender == "" {
			continue
		}
		// Verify it is a local user.
		uid, err := spec.NewUserID(ev.Sender, true)
		if err != nil || uid.Domain() != serverName {
			continue
		}
		localMembers = append(localMembers, ev.Sender)
	}

	if len(localMembers) == 0 {
		return ""
	}

	// Try to get power levels to prefer an admin.
	stateRes := &roomserverAPI.QueryCurrentStateResponse{}
	tuple := gomatrixserverlib.StateKeyTuple{EventType: "m.room.power_levels", StateKey: ""}
	if err := rsAPI.QueryCurrentState(ctx, &roomserverAPI.QueryCurrentStateRequest{
		RoomID:      chatID,
		StateTuples: []gomatrixserverlib.StateKeyTuple{tuple},
	}, stateRes); err == nil {
		if plEvent, ok := stateRes.StateEvents[tuple]; ok {
			var pl powerLevelContent
			if jsonErr := json.Unmarshal(plEvent.Content(), &pl); jsonErr == nil {
				for _, memberID := range localMembers {
					// We need the senderID to look up in pl.Users.
					// For standard rooms senderID == userID string, for pseudo-ID rooms it differs.
					// Try user ID first, then senderID lookup.
					level := pl.UsersDefault
					if v, ok := pl.Users[memberID]; ok {
						level = v
					} else {
						// Try senderID form
						uid, err := spec.NewUserID(memberID, true)
						if err == nil {
							validRoomID, err2 := spec.NewRoomID(chatID)
							if err2 == nil {
								if sid, err3 := rsAPI.QuerySenderIDForUser(ctx, *validRoomID, *uid); err3 == nil && sid != nil {
									if v2, ok2 := pl.Users[string(*sid)]; ok2 {
										level = v2
									}
								}
							}
						}
					}
					if level >= 50 {
						return memberID
					}
				}
			}
		}
	}

	// No admin found – return the first local member.
	return localMembers[0]
}

// getOrCreatePluginDevice retrieves the first existing device for the plugin user,
// or creates a new device with a generated access token if none exist.
func getOrCreatePluginDevice(
	ctx context.Context,
	cfg *config.Dendrite,
	userAPI userapi.ClientUserAPI,
	pluginLocalpart, pluginUserID string,
) (*userapi.Device, error) {
	deviceRes := &userapi.QueryDevicesResponse{}
	if err := userAPI.QueryDevices(ctx, &userapi.QueryDevicesRequest{
		UserID: pluginUserID,
	}, deviceRes); err != nil {
		return nil, fmt.Errorf("QueryDevices: %w", err)
	}

	if len(deviceRes.Devices) > 0 {
		return &deviceRes.Devices[0], nil
	}

	// No device yet – generate an access token and create one.
	token, err := tokens.GenerateLoginToken(tokens.TokenOptions{
		ServerPrivateKey: cfg.Global.PrivateKey.Seed(),
		ServerName:       string(cfg.Global.ServerName),
		UserID:           pluginUserID,
	})
	if err != nil {
		return nil, fmt.Errorf("GenerateLoginToken: %w", err)
	}

	displayName := fmt.Sprintf("Fstick Plugin %s", pluginLocalpart)
	var devRes userapi.PerformDeviceCreationResponse
	if err := userAPI.PerformDeviceCreation(ctx, &userapi.PerformDeviceCreationRequest{
		Localpart:          pluginLocalpart,
		ServerName:         cfg.Global.ServerName,
		DeviceDisplayName:  &displayName,
		AccessToken:        token,
		NoDeviceListUpdate: true,
	}, &devRes); err != nil {
		return nil, fmt.Errorf("PerformDeviceCreation: %w", err)
	}

	return devRes.Device, nil
}

// buildMessageEvent constructs a signed m.room.message PDU for the given device and room.
func buildMessageEvent(
	ctx context.Context,
	content map[string]interface{},
	device *userapi.Device,
	roomID string,
	rsAPI roomserverAPI.ClientRoomserverAPI,
	evTime time.Time,
) (gomatrixserverlib.PDU, error) {
	fullUserID, err := spec.NewUserID(device.UserID, true)
	if err != nil {
		return nil, fmt.Errorf("invalid user ID %q: %w", device.UserID, err)
	}

	validRoomID, err := spec.NewRoomID(roomID)
	if err != nil {
		return nil, fmt.Errorf("invalid room ID %q: %w", roomID, err)
	}

	senderID, err := rsAPI.QuerySenderIDForUser(ctx, *validRoomID, *fullUserID)
	if err != nil {
		return nil, fmt.Errorf("QuerySenderIDForUser: %w", err)
	}
	if senderID == nil {
		return nil, fmt.Errorf("no sender ID found – is the plugin user joined to the room?")
	}

	proto := gomatrixserverlib.ProtoEvent{
		SenderID: string(*senderID),
		RoomID:   roomID,
		Type:     "m.room.message",
	}
	if err := proto.SetContent(content); err != nil {
		return nil, fmt.Errorf("SetContent: %w", err)
	}

	identity, err := rsAPI.SigningIdentityFor(ctx, *validRoomID, *fullUserID)
	if err != nil {
		return nil, fmt.Errorf("SigningIdentityFor: %w", err)
	}

	var queryRes roomserverAPI.QueryLatestEventsAndStateResponse
	e, err := eventutil.QueryAndBuildEvent(ctx, &proto, &identity, evTime, rsAPI, &queryRes)
	if err != nil {
		return nil, fmt.Errorf("QueryAndBuildEvent: %w", err)
	}

	return e.PDU, nil
}

// writeJSON writes a JSON-encoded value to the response with the given status code.
func writeJSON(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

// writeJSONError writes a JSON error response: { "error": "<message>" }.
func writeJSONError(w http.ResponseWriter, code int, message string) {
	writeJSON(w, code, map[string]string{"error": message})
}
