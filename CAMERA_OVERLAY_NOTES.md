# Mappls Surveillance Camera & Safety Features - Implementation Notes

## Current Status
✅ App builds and runs successfully  
✅ Traffic layer attempt added  
✅ Map layers logged and inspected  
✅ Route directions with annotations enabled  

## Why Cameras Don't Appear Yet

The "Surveillance Camera" overlay you see in the official Mappls app requires:

### 1. **Mappls API Key Configuration**
The cameras are tied to specific API key permissions. You need to:
- Check your Mappls API key at https://apis.mappls.com/console/
- Ensure your key has **"Safety & Alerts"** or **"Traffic API"** permissions enabled
- The free tier may not include surveillance camera POI data

### 2. **Map Style URL**
Mappls uses different style endpoints:
```kotlin
// Current default style
MapmyIndiaMap.getInstance(context)

// May need specific style URL with camera POIs
mapplsMap.setStyle("https://apis.mappls.com/advancedmaps/v1/<YOUR_API_KEY>/style?marker=true&safety=true")
```

### 3. **SDK Version Limitation**
- Current SDK: `mapmyindia-android-sdk:7.0.3`
- Surveillance cameras may require:
  - Newer SDK version (8.x or 9.x)
  - **Mappls Navigation SDK** (separate licensed product)
  - **Mappls Safety API** add-on

### 4. **POI Category Filters**
Official app likely uses:
```kotlin
// Filter to show specific POI categories
mapplsMap.showPOICategories(arrayOf("SPEED_CAMERA", "TRAFFIC_CAMERA", "SURVEILLANCE"))
```

But this method may not be in v7.0.3 public API.

## What's Currently Working

1. **Traffic-aware routing** - Annotations enabled in directions
2. **Route hazard detection** - Maneuver points logged
3. **Map POI layers** - Base layers loaded
4. **Custom markers** - Can add our own camera icons

## Next Steps to Get Cameras

### Option A: Upgrade SDK (Recommended)
```gradle
// In app/build.gradle
implementation 'com.mappls.sdk:mappls-android-sdk:9.0.0' // Check latest version
implementation 'com.mappls.sdk:mappls-safety-plugin:1.0.0' // If available
```

### Option B: Use REST API for Camera Data
```kotlin
// Call Mappls Traffic/Safety REST API
// GET https://apis.mappls.com/advancedmaps/v1/<KEY>/safety_alerts
// Add camera markers manually from response
```

### Option C: Check Official Demo
Clone the official demo to see exact implementation:
```bash
git clone https://github.com/mappls-api/mappls-android-sdk
cd mappls-android-sdk/app/src/main/java/com/mappls/sdk/demo
# Check SafetyPluginActivity or TrafficActivity
```

### Option D: Contact Mappls Support
Email: apisupport@mappls.com
- Request documentation for surveillance camera overlay feature
- Ask about API key permissions needed
- Inquire about Safety API access

## Code Currently Added

1. **Traffic annotations** in directions request
2. **Safety overlay logging** to identify available layers
3. **Route marker rendering** for maneuver points
4. **Layer inspection** to see what's available

## Testing the Current Build

1. Open the app
2. Search for "Anna Nagar" (or any location)
3. Request directions
4. Zoom to 16-18x on the route
5. Look for any POI icons that appear

The cameras should appear if:
- Your API key has the right permissions
- You're on a route with known cameras
- The zoom level is appropriate (usually 16+)

## Official Mappls Documentation Links

- SDK Docs: https://github.com/mappls-api/mappls-android-sdk
- API Console: https://apis.mappls.com/console/
- REST API Docs: https://developer.mappls.com/documentation/
- Safety Features: Check under "Navigation APIs" → "Alerts"

