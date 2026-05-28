package routing

import (
	"encoding/json"
	"net/http"

	"github.com/gorilla/mux"
	"github.com/matrix-org/gomatrixserverlib"
	"github.com/matrix-org/gomatrixserverlib/spec"

	roomserverAPI "github.com/element-hq/dendrite/roomserver/api"
)

// ChatMemberResponse is the JSON response body for GET /api/v1/chats/{chat_id}/members/{user_id}.
type ChatMemberResponse struct {
	IsMember bool   `json:"is_member"`
	UserID   string `json:"user_id"`
	Role     string `json:"role"` // "REGULAR" or "ADMIN"
}

// ChatMembersResponse is the JSON response for GET /api/v1/chats/{chat_id}/members.
type ChatMembersResponse struct {
	ChatID  string               `json:"chat_id"`
	Members []ChatMemberResponse `json:"members"`
}

// GetChatMember returns an HTTP handler that queries whether a given user is a member
// of a Matrix room (chat), and what their role is (REGULAR or ADMIN based on power level).
//
// Path variables:
//
//	chat_id - Matrix room ID (e.g. !roomid:server.com)
//	user_id - Matrix user ID (e.g. @user:server.com)
//
// Response:
//
//	{ "is_member": true, "user_id": "@user:server.com", "role": "ADMIN" }
func GetChatMember(rsAPI roomserverAPI.ClientRoomserverAPI) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		vars := mux.Vars(r)
		chatID := vars["chat_id"]
		userIDStr := vars["user_id"]
		ctx := r.Context()

		// Validate user ID format
		fullUserID, err := spec.NewUserID(userIDStr, false)
		if err != nil {
			writeJSONError(w, http.StatusBadRequest, "invalid user_id: must be a fully-qualified Matrix user ID")
			return
		}

		// Query membership status
		membershipRes := &roomserverAPI.QueryMembershipForUserResponse{}
		if err := rsAPI.QueryMembershipForUser(ctx, &roomserverAPI.QueryMembershipForUserRequest{
			RoomID: chatID,
			UserID: *fullUserID,
		}, membershipRes); err != nil {
			writeJSONError(w, http.StatusInternalServerError, "failed to query membership")
			return
		}

		role := "REGULAR"
		if membershipRes.IsInRoom {
			// Query current room state to get power levels
			stateRes := &roomserverAPI.QueryCurrentStateResponse{}
			tuple := gomatrixserverlib.StateKeyTuple{EventType: "m.room.power_levels", StateKey: ""}
			if err := rsAPI.QueryCurrentState(ctx, &roomserverAPI.QueryCurrentStateRequest{
				RoomID:      chatID,
				StateTuples: []gomatrixserverlib.StateKeyTuple{tuple},
			}, stateRes); err == nil {
				if plEvent, ok := stateRes.StateEvents[tuple]; ok {
					role = determinRole(plEvent.Content(), membershipRes.SenderID)
				}
			}
		}

		resp := ChatMemberResponse{
			IsMember: membershipRes.IsInRoom,
			UserID:   userIDStr,
			Role:     role,
		}
		writeJSON(w, http.StatusOK, resp)
	})
}

// GetChatMembers returns an HTTP handler that lists all joined members of a room.
//
// GET /api/v1/chats/{chat_id}/members
//
// Response:
//
//	{ "chat_id": "!room:server", "members": [{"user_id":"@a:s","is_member":true,"role":"ADMIN"}, ...] }
func GetChatMembers(rsAPI roomserverAPI.ClientRoomserverAPI) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		vars := mux.Vars(r)
		chatID := vars["chat_id"]
		ctx := r.Context()

		// Query all joined members
		membersRes := &roomserverAPI.QueryMembershipsForRoomResponse{}
		if err := rsAPI.QueryMembershipsForRoom(ctx, &roomserverAPI.QueryMembershipsForRoomRequest{
			RoomID:     chatID,
			JoinedOnly: true,
		}, membersRes); err != nil {
			writeJSONError(w, http.StatusInternalServerError, "failed to query members")
			return
		}

		// Query power levels once for role determination
		stateRes := &roomserverAPI.QueryCurrentStateResponse{}
		tuple := gomatrixserverlib.StateKeyTuple{EventType: "m.room.power_levels", StateKey: ""}
		_ = rsAPI.QueryCurrentState(ctx, &roomserverAPI.QueryCurrentStateRequest{
			RoomID:      chatID,
			StateTuples: []gomatrixserverlib.StateKeyTuple{tuple},
		}, stateRes)

		members := make([]ChatMemberResponse, 0, len(membersRes.JoinEvents))
		for _, ev := range membersRes.JoinEvents {
			userID := ev.StateKey
			if userID == nil {
				continue
			}
			role := "REGULAR"
			if plEvent, ok := stateRes.StateEvents[tuple]; ok {
				sid := ev.SenderKey
				role = determinRole(plEvent.Content(), &sid)
			}
			members = append(members, ChatMemberResponse{
				IsMember: true,
				UserID:   *userID,
				Role:     role,
			})
		}

		writeJSON(w, http.StatusOK, ChatMembersResponse{
			ChatID:  chatID,
			Members: members,
		})
	})
}

// powerLevelContent is a minimal representation of an m.room.power_levels event content.
type powerLevelContent struct {
	Users        map[string]int64 `json:"users"`
	UsersDefault int64            `json:"users_default"`
}

// determinRole inspects the power levels content and returns "ADMIN" if the user's
// effective power level is >= 50, otherwise "REGULAR".
// The senderID may be a pseudo-ID (for rooms using pseudo-ID room version) or a user ID string.
func determinRole(content []byte, senderID *spec.SenderID) string {
	var pl powerLevelContent
	if err := json.Unmarshal(content, &pl); err != nil {
		return "REGULAR"
	}

	userLevel := pl.UsersDefault
	if senderID != nil {
		if level, ok := pl.Users[string(*senderID)]; ok {
			userLevel = level
		}
	}

	if userLevel >= 50 {
		return "ADMIN"
	}
	return "REGULAR"
}
