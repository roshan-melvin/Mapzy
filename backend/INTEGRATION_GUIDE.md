# 🗺️ Android-Backend Integration Guide

The backend is running at `http://localhost:8000`. To leverage the AI Intelligence layer in your Android app, follow these steps:

## 1. Create a Retrofit Interface
In your Android app, create a new interface to communicate with the FastAPI backend.

```kotlin
interface HazardApiService {
    @Multipart
    @POST("api/v1/reports")
    suspend fun submitReport(
        @Part("user_id") userId: RequestBody,
        @Part("hazard_type") hazardType: RequestBody,
        @Part("description") description: RequestBody,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("severity") severity: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<ReportResponse>
}
```

## 2. Update `ReportRepository.kt`
Currently, your repository inserts directly into Supabase. You should change it to call the backend instead. The backend will handle:
- ✅ Image upload to Cloudinary (same account)
- ✅ AI Verification (YOLO, Gemini, Fake Detection)
- ✅ Supabase storage (the logic is already in `app/api/reports.py`)

### Change this:
```kotlin
// ... inside submitReport ...
com.swapmap.zwap.demo.network.SupabaseManager.client
    .from("reports_analysis")
    .insert(supabaseReport)
```

### To this:
```kotlin
// Call your new HazardApiService here
val response = hazardApiService.submitReport(...)
```

## 3. Handling AI Feedback
The backend returns a `verification_score` and `verification_status`. You can use these to show the user that their report is being verified in real-time.

```json
{
  "report_id": "...",
  "status": "Verified",
  "verification_score": 0.92,
  "ai_reasoning": "Clear image of a hazard detected..."
}
```

## 4. Local Testing
If you are running the backend on your laptop and the Android app on a physical phone or emulator:
- **Emulator**: Use `http://10.0.2.2:8000` as the base URL.
- **Physical Phone**: Ensure both devices are on the same WiFi and use your laptop's Local IP (e.g., `http://192.168.1.X:8000`).

## 🎯 Proactive Task
Would you like me to start modifying the `zwap_native` code to implement this API client for you?
