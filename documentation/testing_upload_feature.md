# Testing Guide: WhatsApp-Style Upload Feature (Final)

## What to Test:

### 1. **Contribution (Reports)**

#### Test Case 1: Instant Visual Feedback
1. Open the app → Go to **Contribute** tab
2. Submit a new report (e.g., "Pothole")
3. **Expected**: Report appears IMMEDIATELY in the feed with:
   - Dark overlay covering the card
   - Spinning progress indicator in the center
   - Status badge shows "Pending" or "Uploading"

#### Test Case 2: Background Upload
1. Submit a report
2. **Immediately close the app** (swipe away from recent apps)
3. Wait 5-10 seconds
4. Reopen the app → Go to Contribute tab
5. **Expected**: 
   - Report is still there (loaded from Room DB)
   - Overlay is visible (still uploading)
   - After a few seconds, overlay disappears (upload complete)

### 2. **Chat Messages (Now Fully Integrated!)**

#### Test Case 1: Instant Message & Progress
1. Go to **Chat** tab → Select a channel
2. Send a message (text or image)
3. **Expected**: 
   - Message appears immediately
   - "Sending..." overlay (dark background + spinner) appears over the message bubbles

#### Test Case 2: Retry Mechanism (Mock Failure)
1. Turn off WiFi / Mobile Data
2. Send a message
3. **Expected**: 
   - Message appears with overlay
   - After timeout/failure, overlay disappears
   - **Red Retry Icon** appears next to timestamp
   - Status notification says "Message Failed"
4. Turn on Internet
5. **Click the Retry Icon**
6. **Expected**:
   - Status changes back to "Sending" (overlay returns)
   - Message sends successfully (overlay disappears)

#### Test Case 3: Offline Persistence
1. Send a message while offline
2. Close the app completely
3. Reopen app
4. **Expected**: Message is still there in the chat list (loaded from local DB) with "Failed" state (retry icon visible).

---

## Technical Verification

- **Database**: Check `AppDatabase` (version 2) contains `pending_reports` and `pending_messages` tables.
- **Workers**: `UploadWorker` (Reports) and `ChatUploadWorker` (Chat) running in background.
