package streams

import (
	"context"
	"encoding/json"
	"sync"
	"sync/atomic"

	"github.com/element-hq/dendrite/syncapi/notifier"
	"github.com/element-hq/dendrite/syncapi/storage"
	"github.com/element-hq/dendrite/syncapi/types"
)

// fstickPositionedEvent is an internal record tying a stream position to an event.
type fstickPositionedEvent struct {
	pos   types.StreamPosition
	event types.FstickClientEvent
}

// FstickEventStore is an in-memory store for custom fstick events.
// It is created once at the monolith level and shared between the syncapi
// (which reads events during /sync) and the fstickapi (which pushes events).
type FstickEventStore struct {
	mu      sync.RWMutex
	counter int64                              // monotonic counter, accessed via sync/atomic
	events  map[string][]fstickPositionedEvent // userID -> ordered event list
	onPush  func(userID string, pos types.StreamPosition)
}

// NewFstickEventStore creates a ready-to-use FstickEventStore.
func NewFstickEventStore() *FstickEventStore {
	return &FstickEventStore{
		events: make(map[string][]fstickPositionedEvent),
	}
}

// SetPushCallback registers a function called every time a new event is pushed.
// The syncapi calls this once to wire up stream advancement and notifier wake-ups.
func (s *FstickEventStore) SetPushCallback(fn func(userID string, pos types.StreamPosition)) {
	s.mu.Lock()
	s.onPush = fn
	s.mu.Unlock()
}

// Push stores a new custom event for userID and wakes any waiting /sync requests.
func (s *FstickEventStore) Push(userID, eventType string, content json.RawMessage) {
	pos := types.StreamPosition(atomic.AddInt64(&s.counter, 1))
	pe := fstickPositionedEvent{
		pos: pos,
		event: types.FstickClientEvent{
			Type:    eventType,
			Content: content,
		},
	}

	s.mu.Lock()
	s.events[userID] = append(s.events[userID], pe)
	cb := s.onPush
	s.mu.Unlock()

	if cb != nil {
		cb(userID, pos)
	}
}

// GetSince returns all events for userID with position in the range (from, to].
func (s *FstickEventStore) GetSince(userID string, from, to types.StreamPosition) []types.FstickClientEvent {
	s.mu.RLock()
	userEvents := s.events[userID]
	s.mu.RUnlock()

	var result []types.FstickClientEvent
	for _, pe := range userEvents {
		if pe.pos > from && pe.pos <= to {
			result = append(result, pe.event)
		}
	}
	return result
}

// LatestPosition returns the current highest stream position in the store.
func (s *FstickEventStore) LatestPosition() types.StreamPosition {
	return types.StreamPosition(atomic.LoadInt64(&s.counter))
}

// ---------------------------------------------------------------------------
// FstickStreamProvider implements StreamProvider for fstick custom events.
// ---------------------------------------------------------------------------

// FstickStreamProvider satisfies the StreamProvider interface and delivers
// custom fstick events to clients through the /sync endpoint.
type FstickStreamProvider struct {
	DefaultStreamProvider
	store    *FstickEventStore
	notifier *notifier.Notifier
}

// Setup initialises the provider position from the store and registers the
// push-callback so that future pushes advance the stream and wake /sync clients.
func (p *FstickStreamProvider) Setup(
	ctx context.Context, snapshot storage.DatabaseTransaction,
) {
	p.DefaultStreamProvider.Setup(ctx, snapshot)

	// Seed the latest position from whatever the store already has.
	p.Advance(p.store.LatestPosition())

	// Wire up the push callback: advance position + wake the notified user.
	p.store.SetPushCallback(func(userID string, pos types.StreamPosition) {
		p.Advance(pos)
		p.notifier.OnNewFstickEvent(userID, types.StreamingToken{FstickEventPosition: pos})
	})
}

// CompleteSync delivers all fstick events available for the requesting user.
func (p *FstickStreamProvider) CompleteSync(
	ctx context.Context,
	snapshot storage.DatabaseTransaction,
	req *types.SyncRequest,
) types.StreamPosition {
	return p.IncrementalSync(ctx, snapshot, req, 0, p.LatestPosition(ctx))
}

// IncrementalSync delivers fstick events in position range (from, to] for the user.
func (p *FstickStreamProvider) IncrementalSync(
	ctx context.Context,
	snapshot storage.DatabaseTransaction,
	req *types.SyncRequest,
	from, to types.StreamPosition,
) types.StreamPosition {
	events := p.store.GetSince(req.Device.UserID, from, to)
	req.Response.FstickEvents = append(req.Response.FstickEvents, events...)
	return to
}
