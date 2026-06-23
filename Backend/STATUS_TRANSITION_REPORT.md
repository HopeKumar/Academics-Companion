# Status Transition Consistency Report

This report outlines the enhancements made to ensure consistent and standard status representations across async tasks and documents in Campus LM.

---

## 1. Enum Standardization (`ProcessingStatus`)

Previously, background task execution (such as summary, quiz, flashcard, and mindmap generation) mixed states (e.g., `READY` vs. `COMPLETED`).
We updated `ProcessingStatus.java` by renaming the `READY` status to `COMPLETED` to create a standard, clear lifecycle:
- **`PENDING`** -> Initial queued state.
- **`PROCESSING`** -> Active execution state.
- **`COMPLETED`** -> Task completed successfully.
- **`FAILED`** -> Task failed.

---

## 2. Document Model Integration

We updated the `Document` model to include a new dedicated field to track podcast generation status:
- **Field**: `podcastStatus` (mapped via `@Field("podcast_status")`)
- **Type**: `ProcessingStatus` (defaulting to `ProcessingStatus.PENDING` on creation)

---

## 3. Transition Handlers Updated

We updated all downstream services to transition statuses using the new unified `COMPLETED` name:
1. **`DocumentProcessingService.java`**:
   - Updates document embedding, summary, flashcard, quiz, and mindmap fields to `ProcessingStatus.COMPLETED` upon successful execution.
2. **`AsyncProcessingOrchestrator.java`**:
   - Updates the global metadata `status` field of a Source to `COMPLETED` (instead of `READY`) when all async feature generations are finished.
3. **`SourceService.java`**:
   - Maintains a backward-compatible check looking for both `"READY"` and `"COMPLETED"` to determine if progress is 100% and returns the normalized status `"COMPLETED"`.
