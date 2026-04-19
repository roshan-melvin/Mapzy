# WhatsApp-Style Background Upload Implementation

## Current Status: ✅ Backend Complete, ⚠️ UI Integration Pending

### What's Working:
1. **Background Upload Workers**
   - `UploadWorker.kt`: Handles report uploads (Firestore + Supabase)
   - `ChatUploadWorker.kt`: Handles chat message uploads (Firestore only)
   
2. **Local Database (Room)**
   - `PendingReport` entity created
   - `PendingReportDao` with CRUD operations
   - `AppDatabase` singleton initialized
   
3. **WorkManager Integration**
   - Reports are enqueued in `MainActivity.submitReport()`
   - Chat messages are enqueued in `ChatFragment.sendMessage()`
   
4. **Notifications**
   - Progress notifications during upload
   - Success/failure notifications
   - Permissions added to AndroidManifest

### What's NOT Working Yet:

#### 1. **UI doesn't show pending items**
**Problem**: When you submit a report/message, it goes to Room DB and WorkManager starts uploading in background. BUT the UI (ContributionFragment, ChatFragment) only reads from Firestore, not from the local Room DB.

**Solution Needed**:
- Modify `ContributionFragment` to:
  ```kotlin
  // Fetch from BOTH sources
  val firestoreReports = fetchFromFirestore()
  val pendingReports = AppDatabase.getDatabase(context).pendingReportDao().getAllPendingReports()
  
  // Merge and display with visual indicator (blur/spinner) for pending items
  ```

#### 2. **No visual feedback during upload**
**Problem**: User doesn't see a "blurred" or "uploading..." placeholder in the feed.

**Solution Needed**:
- Update `item_report_card.xml` to include:
  - Overlay view with blur effect
  - Progress indicator
  - Visibility controlled by `report.status == "Uploading"`

#### 3. **Retry mechanism not implemented**
**Problem**: If upload fails, the item stays in DB with status="Failed", but there's no UI button to retry.

**Solution Needed**:
- Add tap listener on failed items
  - Re-enqueue the WorkManager task
  - Update status back to "Uploading"

### Next Steps:

1. **Update ContributionFragment** to read from Room DB
2. **Update ChatFragment** to read from Room DB (for chat messages)
3. **Add visual indicators** (blur overlay, progress bar)
4. **Implement retry UI**

### Testing Checklist:
- [ ] Submit report → See it immediately in feed with "Uploading..." overlay
- [ ] Close app → Reopen → Pending report still visible
- [ ] Wait for upload → Overlay disappears, real data from Firestore shows
- [ ] Turn off internet → Submit report → See "Failed" state → Tap to retry
- [ ] Same tests for Chat messages

---

## Code Locations:

**Workers:**
- `/app/src/main/java/com/swapmap/zwap/demo/workers/UploadWorker.kt`
- `/app/src/main/java/com/swapmap/zwap/demo/workers/ChatUploadWorker.kt`

**Database:**
- `/app/src/main/java/com/swapmap/zwap/demo/db/PendingReport.kt`
- `/app/src/main/java/com/swapmap/zwap/demo/db/PendingReportDao.kt`
- `/app/src/main/java/com/swapmap/zwap/demo/db/AppDatabase.kt`

**Integration Points:**
- `MainActivity.submitReport()` - Line 308-354
- `ChatFragment.sendMessage()` - Line 253-277

**UI (Needs Update):**
- `ContributionFragment.kt`
- `ChatFragment.kt`
- `item_report_card.xml` (add overlay)
