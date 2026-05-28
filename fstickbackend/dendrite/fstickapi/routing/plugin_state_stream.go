package routing

import (
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
)

type pluginStateSubscriber struct {
	ch     chan json.RawMessage
	closed chan struct{}
}

// PluginStateBroker manages SSE subscriptions.
// Key format:
//   - shared: "chatId:pluginId"         → broadcasts to all users
//   - user:   "chatId:pluginId:userId"  → broadcasts to one user only
type PluginStateBroker struct {
	mu          sync.RWMutex
	subscribers map[string][]*pluginStateSubscriber
}

func NewPluginStateBroker() *PluginStateBroker {
	return &PluginStateBroker{subscribers: make(map[string][]*pluginStateSubscriber)}
}

func brokerKey(chatID, pluginID, userID string) string {
	if userID == "" {
		return chatID + ":" + pluginID
	}
	return chatID + ":" + pluginID + ":" + userID
}

func (b *PluginStateBroker) Subscribe(chatID, pluginID, userID string) (*pluginStateSubscriber, func()) {
	sub := &pluginStateSubscriber{ch: make(chan json.RawMessage, 16), closed: make(chan struct{})}
	key := brokerKey(chatID, pluginID, userID)
	b.mu.Lock()
	b.subscribers[key] = append(b.subscribers[key], sub)
	b.mu.Unlock()
	return sub, func() {
		close(sub.closed)
		b.mu.Lock()
		subs := b.subscribers[key]
		out := subs[:0]
		for _, s := range subs {
			if s != sub {
				out = append(out, s)
			}
		}
		b.subscribers[key] = out
		b.mu.Unlock()
	}
}

// Broadcast sends payload to the channel keyed by chatID+pluginID+userID.
// userID="" → shared channel (all subscribers).
// userID!="" → only that user's channel.
func (b *PluginStateBroker) Broadcast(chatID, pluginID, userID string, payload json.RawMessage) {
	key := brokerKey(chatID, pluginID, userID)
	b.mu.RLock()
	subs := make([]*pluginStateSubscriber, len(b.subscribers[key]))
	copy(subs, b.subscribers[key])
	b.mu.RUnlock()
	for _, sub := range subs {
		select {
		case sub.ch <- payload:
		case <-sub.closed:
		default:
		}
	}
}

type PluginStatePushRequest struct {
	PluginID string          `json:"plugin_id"`
	ChatID   string          `json:"chat_id"`
	// UserID: empty → shared broadcast; non-empty → push to single user
	UserID string          `json:"user_id,omitempty"`
	State  json.RawMessage `json:"state"`
}

func PluginStatePush(broker *PluginStateBroker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req PluginStatePushRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, `{"error":"bad request"}`, http.StatusBadRequest)
			return
		}
		if req.PluginID == "" || req.ChatID == "" {
			http.Error(w, `{"error":"plugin_id and chat_id are required"}`, http.StatusBadRequest)
			return
		}
		broker.Broadcast(req.ChatID, req.PluginID, req.UserID, req.State)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"ok":true}`))
	}
}

// PluginStateSubscribe SSE endpoint.
// Subscribes to BOTH shared channel AND user-scoped channel (if user_id provided).
func PluginStateSubscribe(broker *PluginStateBroker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		chatID   := r.URL.Query().Get("chat_id")
		pluginID := r.URL.Query().Get("plugin_id")
		userID   := r.URL.Query().Get("user_id")
		if chatID == "" || pluginID == "" {
			http.Error(w, `{"error":"chat_id and plugin_id are required"}`, http.StatusBadRequest)
			return
		}
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "streaming not supported", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")
		w.Header().Set("Connection", "keep-alive")
		w.Header().Set("Access-Control-Allow-Origin", "*")
		_, _ = fmt.Fprintf(w, ": connected\n\n")
		flusher.Flush()

		ctx := r.Context()
		sharedSub, sharedCleanup := broker.Subscribe(chatID, pluginID, "")
		defer sharedCleanup()

		var userCh <-chan json.RawMessage
		if userID != "" {
			userSub, userCleanup := broker.Subscribe(chatID, pluginID, userID)
			defer userCleanup()
			userCh = userSub.ch
		}

		send := func(payload json.RawMessage) {
			_, _ = fmt.Fprintf(w, "data: %s\n\n", payload)
			flusher.Flush()
		}

		for {
			select {
			case <-ctx.Done():
				return
			case p, ok := <-sharedSub.ch:
				if !ok { return }
				send(p)
			case p, ok := <-userCh:
				if !ok { return }
				send(p)
			}
		}
	}
}

