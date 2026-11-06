# Entry Draft Persistence

MarkDay protects an entry editor with two complementary restoration layers:

1. Compose `rememberSaveable` state restores the currently visible form across recomposition and ordinary saved-state
   recreation.
2. `EntryDraftRepository` stores meaningful form snapshots outside the UI lifecycle so drafts survive navigation resets,
   browser refreshes, and process restarts.

Drafts are recovery data, not diary entries. They remain on the current device and are deliberately excluded from cloud
sync, import, export, and the Room diary-entry schema.

## Components

| Component | Responsibility |
| --- | --- |
| `EntryDraft` | Serializable snapshot of every editable field plus identity and source-revision metadata. |
| `EntryDraftRepository` | Loads, replaces, and deletes drafts by logical editor key. |
| `PersistentEntryDraftRepository` | Stores all drafts in a versioned JSON envelope and serializes read-modify-write operations. |
| `EntryDetailsScreen` | Hydrates the form, produces snapshots, manages save status, and coordinates commit/discard behavior. |
| `EditorExitGuard` | Gives app and platform navigation a safe way to flush the current snapshot before leaving. |
| Platform storage | DataStore on Android/Desktop and browser `localStorage` on Web. |

The persistent JSON document uses one storage key because DataStore preferences and `localStorage` provide the same
simple key/value abstraction. The repository mutex prevents two autosaves in the repository singleton from losing each
other during a read-modify-write operation.

## Draft identity

There are two logical draft key forms:

- `EntryDraftKey.NewEntry` identifies the single interrupted new-entry draft.
- `EntryDraftKey.ExistingEntry(syncId)` identifies a parked edit for an existing diary entry.

A new draft receives its eventual diary `syncId` and `createdAt` as soon as editing starts. Those values must remain stable
for the draft's lifetime. Besides preserving diary semantics, the stable `syncId` closes a failure window: if the diary
entry commits but deleting its draft fails, startup can see that the target entry already exists and remove the stale
draft instead of reopening it as a duplicate.

An existing-entry draft records the source entry's `updatedAt`. On restoration, a different current `updatedAt` means the
entry changed after the draft was based on it, usually because of cloud sync. The editor requires an explicit choice:

- **Keep draft** accepts that the draft may replace the newer entry and rebases its source revision.
- **Reload current** deletes the stale draft and uses the current committed entry.

No automatic field-level merge is attempted.

## Editor lifecycle

### Hydration

The editor initializes saveable values, but keeps its fields disabled until durable draft loading completes. Autosave is
also disabled during this phase. This ordering is an invariant: observing the initial empty form before hydration could
overwrite the draft that is about to be restored.

For a new entry, `EntriesScreen` checks the draft repository before presenting the entries list. An interrupted draft is
opened automatically. Existing-entry drafts are restored when the user enters Edit mode for that entry.

### Autosave

The form is converted to an immutable snapshot whenever an editable field changes. The autosave flow:

1. Drops the initial observed snapshot because hydration already established its persistence state.
2. Marks a changed snapshot as `SAVING` immediately.
3. Coalesces further changes for 750 ms.
4. Persists the newest snapshot.
5. Reports `SAVED` only if the form still equals the snapshot that completed persistence.

That last comparison prevents a slow write from briefly claiming that newer, still-unpersisted text is saved. If a
configuration recreation restores a form whose status was `SAVING`, the editor schedules that current snapshot again
before beginning normal observation.

The persistence states have these meanings:

| State | Meaning |
| --- | --- |
| `IDLE` | The form matches its committed baseline and no durable draft is needed. |
| `SAVING` | The latest values are waiting for the debounce or are being written. |
| `SAVED` | The durable draft matches the current form. |
| `FAILED` | The latest form could not be written; Retry or a later edit can try again. |

An untouched new-entry form is never stored. When an existing form returns exactly to the committed-entry baseline, the
repository entry is deleted and the state returns to `IDLE`.

### Save, Back, and Discard

- **Save** first attempts to persist the current form, then commits the diary entry. A failed diary commit leaves the
  draft and editor intact. After a successful commit, draft deletion is best-effort; stable identity handles interrupted
  cleanup on the next startup.
- **Back/Close** retains a persisted draft. If the latest values are pending, the editor bypasses the debounce and flushes
  immediately. A warning appears only when that flush fails.
- **Discard** is the explicit destructive path. It confirms intent, deletes the durable draft, and returns to the
  committed entry or entries list.

## Exit protection

`EditorExitGuard` is published only while an editor is active. In-app tab/profile navigation and platform exit handlers
use the same rule: leave immediately when no unpersisted values exist; otherwise try an immediate flush and warn only on
failure.

Platform behavior is intentionally small:

- Android intercepts system Back and invokes the editor's protected close action.
- Desktop attempts a flush from the window close request and presents `Stay` or `Close anyway` if it fails.
- Web installs a `beforeunload` listener only while the current form is not persisted.

An abrupt process kill cannot run asynchronous cleanup. Recovery is therefore bounded by the last completed autosave,
with saved-instance state providing an additional short-lived restoration layer where the platform supports it.

## Storage and schema rules

- The JSON envelope has an explicit schema version. Unknown fields are accepted for forward-compatible additions, but a
  document written by a newer unsupported schema is rejected.
- Decode or validation failures throw `EntryDraftCorruptionException`. The repository must not overwrite the unreadable
  document automatically; doing so would turn a recoverable storage problem into certain data loss.
- `EntryDraft` validates its ISO date and stable identifiers during decoding so malformed storage cannot crash later in
  Compose rendering.
- `write(null)` means that the platform storage key should be removed after the last draft is deleted.
- Draft contents are plain local application data and are not encrypted by this feature. Platform backup, browser
  profile, and device-access policies still apply.

When changing the serialized shape, prefer optional fields with defaults for compatible additions. Increment the schema
version only when older code must reject the new representation, and add a migration before raising the version if
existing drafts should remain recoverable.

## Verification focus

Repository tests cover independent draft keys, serialization round trips, deletion, malformed-document preservation,
storage failures, and the 750 ms debounce. When changing lifecycle behavior, manually verify at least one new-entry and
one existing-entry draft across Back/navigation, plus the relevant platform restart or refresh path.
