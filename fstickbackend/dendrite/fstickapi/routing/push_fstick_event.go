package routing

import (
	"encoding/json"
	"net/http"

	"github.com/element-hq/dendrite/syncapi/streams"
	"github.com/sirupsen/logrus"
)

// pushFstickEventRequest is the body accepted by POST /fstick/api/v1/events/push.
//
// Example:
//
//	{
//	  "user_id": "@alice:example.com",
//	  "type":    "fstick.plugin.notification",
//	  "content": { "text": "Hello!" }
//	}
type pushFstickEventRequest struct {
	UserID  string          `json:"user_id"`
	Type    string          `json:"type"`
	Content json.RawMessage `json:"content"`
}

// PushFstickEvent returns an HTTP handler that accepts custom plugin events
// and delivers them to a specific user via their next /sync response.
//
// POST /fstick/api/v1/events/push
func PushFstickEvent(store *streams.FstickEventStore) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var req pushFstickEventRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, `{"error":"bad request: `+err.Error()+`"}`, http.StatusBadRequest)
			return
		}

		if req.UserID == "" {
			http.Error(w, `{"error":"user_id is required"}`, http.StatusBadRequest)
			return
		}
		if req.Type == "" {
			http.Error(w, `{"error":"type is required"}`, http.StatusBadRequest)
			return
		}

		store.Push(req.UserID, req.Type, req.Content)

		logrus.WithFields(logrus.Fields{
			"user_id": req.UserID,
			"type":    req.Type,
		}).Info("fstickapi: pushed custom event to sync")

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{}`))
	})
}
