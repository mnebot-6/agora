# Guest Position Selection for LIMITED_WITH_POSITIONS Activities

**Date:** 2026-06-22
**Status:** Approved
**Scope:** When a guest requests to join an activity via the web landing (`/a/{code}`), and the activity uses `slot_mode = 'limited_with_positions'`, the guest selects preferred positions. The admin approves and the system auto-assigns the best slot.

## Context

Activities with `limited_with_positions` have:
- **Slot groups** (`slot_groups`): organizational grouping (e.g. "Equipo A", "Equipo B").
- **Positions** (`positions`): roles a slot can accept (e.g. "Portero", "Defensa").
- **Slots** (`slots`): individual bookable spots, each belonging to a group.
- **Slot-position mapping** (`slot_positions`): N:M — a slot can accept multiple positions.

Currently, `request_guest_slot` ignores positions entirely and grabs the first available slot by `sort_order`. This is incorrect for `limited_with_positions` because the guest ends up in a random slot with no regard for their role.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Guest selects... | Positions (not slots) | Guest doesn't know internal slot structure; they know what role they play |
| Multi-position | Yes, one or more | "I can play Defensa or Central" — more flexibility for admin |
| Slot retention timing | At approval (not request) | Don't block slots for requests that may be rejected |
| Slot assignment | Auto-assign most restrictive | Greedy: prefer slots with fewer accepted positions to preserve flexibility |
| Admin override | No manual slot pick | Auto-assignment with smart ordering is sufficient; keeps UI simple |
| Affected modes | Only `limited_with_positions` | `unlimited` and `limited` keep current behavior unchanged |

## Data Model Changes

### `activity_guest_requests` — new column

```sql
ALTER TABLE activity_guest_requests
    ADD COLUMN requested_position_ids uuid[] DEFAULT NULL;
```

- `NULL` for requests from `unlimited`/`limited` activities (backward compatible).
- Non-empty array for `limited_with_positions` requests.
- `slot_id` will be `NULL` at request time for `limited_with_positions`; filled at approval.

## RPC Changes

### `get_activity_guest_preview` — add positions to response

When the activity is `limited_with_positions`, include a `positions` array:

```json
"positions": [
  { "id": "uuid-1", "name": "Portero", "available": 1 },
  { "id": "uuid-2", "name": "Defensa", "available": 2 },
  { "id": "uuid-3", "name": "Central", "available": 0 }
]
```

**Availability calculation:** For each position, count the number of slots that:
1. Have `status = 'available'`
2. Accept that position (via `slot_positions`)

Positions with 0 available are included (shown disabled in UI). Positions with no slots at all are excluded.

### `request_guest_slot` — new parameter, conditional behavior

**Signature change:**
```sql
CREATE OR REPLACE FUNCTION request_guest_slot(
    p_code text,
    p_name text,
    p_phone text,
    p_position_ids uuid[] DEFAULT NULL
) RETURNS jsonb
```

**For `limited_with_positions`:**
1. Validate `p_position_ids` is not empty.
2. Validate all position IDs exist and belong to the activity.
3. Validate at least one slot exists that is `available` and accepts one of the requested positions (prevent obviously hopeless requests).
4. Create `activity_guest_requests` row with `requested_position_ids = p_position_ids`, `slot_id = NULL`.
5. Do NOT retain any slot.
6. Return `{ "status": "pending", "request_id": "..." }`.

**For `unlimited` and `limited`:**
- No change. `p_position_ids` is ignored (default NULL). Slot retained as before.

### `approve_guest_request` — auto-assign slot for position requests

When `v_req.requested_position_ids IS NOT NULL`:

1. Find available slots:
   ```sql
   SELECT s.id, COUNT(sp.position_id) AS pos_count
   FROM slots s
   JOIN slot_positions sp ON sp.slot_id = s.id
   WHERE s.activity_id = v_req.activity_id
     AND s.status = 'available'
     AND sp.position_id = ANY(v_req.requested_position_ids)
   GROUP BY s.id
   ORDER BY pos_count ASC, s.sort_order ASC
   LIMIT 1
   FOR UPDATE SKIP LOCKED;
   ```
2. If no slot found: `RAISE EXCEPTION 'No available slot for the requested positions'`.
3. Assign: `UPDATE slots SET status = 'reserved', reserved_by = v_req.user_id, reserved_at = now(), is_guest = true WHERE id = v_slot_id`.
4. Update request: `SET slot_id = v_slot_id, status = 'approved', ...`.
5. Update profile display name (from migration 025).

When `v_req.requested_position_ids IS NULL`:
- No change (slot already retained from request time).

### `list_pending_guest_requests` — include position names

Add position names to each pending request in the response:

```json
{
  "id": "...",
  "guest_name": "Carlos",
  "guest_phone": "...",
  "requested_at": "...",
  "requested_positions": ["Defensa", "Central"]
}
```

Resolved by joining `requested_position_ids` against the `positions` table.

## Web Landing Changes (`web/a/index.html`)

### Preview rendering

When `preview.activity.slot_mode === 'limited_with_positions'` and `preview.positions` is non-empty:
- Render a checkbox group between the phone input and the submit button.
- Each checkbox: `"Defensa (2 libres)"` — enabled if `available > 0`, disabled otherwise.
- At least one checkbox must be selected to submit.

### Submit

Pass `p_position_ids` as an array of selected position UUIDs to the RPC call.

### No positions (other modes)

No change. The form works as before without the checkbox section.

## App Changes (Kotlin)

### `PendingGuestRequest` model

Add field:
```kotlin
@SerialName("requested_positions") val requestedPositions: List<String> = emptyList()
```

### `GuestRequestRow` (ActivityDetailScreen)

Show requested positions as chips/text below the guest name:
```
Carlos — Defensa, Central
  [Aprobar] [Rechazar]
```

### Error handling on approve

If approve fails with "No available slot", show the error message in a snackbar instead of silently failing.

## Backward Compatibility

- `p_position_ids` defaults to `NULL` — existing callers (web landing for `unlimited`/`limited`) don't break.
- `requested_position_ids` column defaults to `NULL` — existing rows unaffected.
- `approve_guest_request` branches on `requested_position_ids IS NULL` vs not — old requests follow old path.

## Not in Scope

- Admin manually picking a slot (auto-assignment covers this).
- Guest seeing group structure (unnecessary complexity).
- Position selection in the native app guest flow (`GuestActivityScreen.kt`) — only web landing for now.
