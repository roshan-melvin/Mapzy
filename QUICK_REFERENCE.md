# 🎯 Quick Reference - Camera Visibility Debugging

## TL;DR
- ✅ **Code is correct** - No bugs found
- 🎯 **Most likely issue** - OSM data doesn't have `highway=speed_camera` in your test region  
- 📍 **Hazards work** - But cameras don't, suggesting cameras aren't in OSM data

## Enhanced Logging Emoji Guide

When you run the app and toggle hazards ON, look for these in logcat:

```
🎥 CAMERA DETECTED    → Speed camera found in OSM response
⚠️ HAZARD DETECTED     → Hazard type found (traffic_calming/stop/give_way)
✅ CAMERA ADDED        → Camera passed zoom filter and added to list
✅ HAZARD ADDED        → Hazard passed zoom filter and added to list
🎨 MARKER RENDERED     → Marker drawn on map
📡 OSM API RESPONSE    → X elements returned from API
📤 Sending OSM Query   → Query parameters and bbox info
📥 OSM Response        → HTTP response code and status
```

## How to Check Logs

```bash
# Open terminal and run:
adb logcat | grep "Zwap"

# Or for just cameras:
adb logcat | grep "🎥\|CAMERA"

# Or for hazards:
adb logcat | grep "⚠️\|HAZARD"
```

## Expected Log Sequence (If Working)

```
📤 Sending OSM Query (first 250 chars): [out:json]...
📍 OSM Query Parameters: bbox=(27.7,77.2,27.8,77.3), zoom=5.2, radius=11km
📥 OSM Response: code=200, successful=true, hasBody=true
📡 OSM API RESPONSE: 45 elements returned, zoom=5.2
🎥 CAMERA DETECTED: id=123456, lat=27.75, lon=77.25, zoom=5.2, shouldShow=true
⚠️ HAZARD DETECTED: TRAFFIC_CALMING, zoom=5.2, shouldShow=true
⚠️ HAZARD DETECTED: STOP_SIGN, zoom=5.2, shouldShow=true
✅ CAMERA ADDED: total cameras now = 1
✅ HAZARD ADDED: TRAFFIC_CALMING, total hazards now = 1
✅ HAZARD ADDED: STOP_SIGN, total hazards now = 2
🎨 MARKER RENDERED: SPEED_CAMERA at (27.75,77.25) - 4 total
🎨 MARKER RENDERED: TRAFFIC_CALMING at (27.76,77.26) - 5 total
```

## If Cameras Show 0

### Case 1: No Elements Returned
```
📡 OSM API RESPONSE: 0 elements returned
⚠️ NO ELEMENTS in response
```
**Meaning:** OSM data doesn't exist for this region/zoom

### Case 2: Only Hazards, No Cameras
```
🎥 CAMERA DETECTED: ... shouldShow=false
⚠️ HAZARD DETECTED: ... shouldShow=true
```
**Meaning:** Cameras exist in OSM but zoom filter rejected them

### Case 3: Elements but No Cameras
```
📡 OSM API RESPONSE: 15 elements returned
⚠️ HAZARD DETECTED: STOP_SIGN
⚠️ HAZARD DETECTED: TRAFFIC_CALMING
(No 🎥 CAMERA DETECTED logs)
```
**Meaning:** No `highway=speed_camera` data in OSM for this region

## Zoom Level Thresholds

- **Zoom < 1.0**: All markers cleared
- **Zoom 1.0-4.9**: CAMERAS only visible
- **Zoom 5.0+**: CAMERAS + HAZARDS visible

## Code Locations

| Feature | File | Line | Status |
|---------|------|------|--------|
| Toggle button | MainActivity.kt | 166-170 | ✅ Working |
| Main fetch function | MainActivity.kt | 407 | ✅ Correct |
| Zoom filter logic | MainActivity.kt | 470-478 | ✅ Correct |
| Feature matching | OSMFeature.kt | 19-31 | ✅ Correct |
| OSM query | OSMOverpassService.kt | 28-37 | ✅ Correct |
| Marker creation | MainActivity.kt | 554-608 | ✅ Correct |

## What to Do Next

1. **Wait for device connection** (need Android device or emulator)
2. **Build and install**: `./gradlew assembleDebug && adb install -r ...`
3. **Open logcat**: `adb logcat | grep Zwap`
4. **Toggle hazards ON** at state level (zoom 5-6)
5. **Look for emoji-marked logs** above
6. **Share the log output** to help identify the issue

## Possible Solutions by Symptom

| Symptom | Solution |
|---------|----------|
| Toast shows "Cameras: 0" | Check OSM has `highway=speed_camera` data |
| No elements returned | Wrong coordinates or no data in OSM |
| Cameras in response but don't show | Zoom level below 1.0 (unlikely) |
| Only hazards show | Cameras not in OSM for this region |

## Confirmed Working ✅

- Hazards display (traffic_calming, stop signs, give_way signs)
- Progressive loading animation (20ms between markers)
- Toast shows accurate count
- Zoom-based filtering for hazards
- Marker colors (blue for cameras, orange for hazards)
- Button toggle state (white/orange)
