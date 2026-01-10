# Zwap - Smartphone-Based Speed Camera Detector

A mobile-first navigation assistant with real-time safety alerts for speed cameras, traffic signals, and road hazards.

## 📱 Project Information

- **App Name:** Zwap
- **Package Name:** `com.swapmap.zwap`
- **Platform:** Flutter (Android primary, iOS future)
- **Map Provider:** Mappls (MapmyIndia)
- **Data Source:** OpenStreetMap (Overpass API)

## 🏗️ Project Structure

```
zwap/
├── android/          # Android-specific configuration
├── ios/              # iOS-specific configuration (future)
├── lib/              # Flutter application code
│   ├── main.dart     # App entry point
│   ├── screens/      # UI screens (Phase 1)
│   ├── services/     # Business logic (Phase 1)
│   ├── models/       # Data models (Phase 1)
│   └── widgets/      # Reusable UI components (Phase 1)
├── test/             # Unit and widget tests
└── PHASE0_SETUP.md   # Setup instructions
```

## 🚀 Current Status

**Phase 0: Platform & Security Setup** ✅ (In Progress)
- ✅ Flutter project created
- ✅ Package name configured: `com.swapmap.zwap`
- ⏳ Android SDK setup required
- ⏳ SHA-256 certificate extraction pending
- ⏳ Mappls API registration pending

## 📋 Development Phases

### Phase 0: Platform & Security (Current)
- Android package name and signing configuration
- SHA-256 certificate extraction
- Mappls API access token setup
- Secure token storage

### Phase 1: Core Mobile Features (Next)
- Map display and GPS navigation
- Speed detection and monitoring
- Speed camera alerts (OSM-based)
- Road hazard and traffic signal alerts
- Audio alert system

### Phase 2: Web MVP
- Enhance existing web app for data visualization
- OSM data layer integration
- Mark mobile-only features

### Phase 3: Testing & Validation
- Alert timing accuracy
- Battery consumption analysis
- Offline mode testing

### Phase 4: Community Features (Future)
- User reporting system
- Backend and database
- Data validation and confidence scoring

## 🛠️ Setup Instructions

See [PHASE0_SETUP.md](./PHASE0_SETUP.md) for detailed setup instructions.

**Quick Start:**
1. Install Android Studio or Android SDK
2. Run `flutter doctor` to verify setup
3. Extract SHA-256 certificate
4. Register with Mappls API
5. Add access token to `android/key.properties`

## 🔐 Security

- API tokens stored in `android/key.properties` (gitignored)
- Keystores never committed to git
- SHA-256 certificate required for API access

## 📚 Documentation

- [Implementation Plan](../.gemini/antigravity/brain/5221c656-7f92-42c4-bc7c-db0436e74686/implementation_plan.md)
- [Architecture](../.gemini/antigravity/brain/5221c656-7f92-42c4-bc7c-db0436e74686/architecture.md)
- [Phase 0 Guide](../.gemini/antigravity/brain/5221c656-7f92-42c4-bc7c-db0436e74686/phase0_guide.md)

## 🤝 Contributing

This is a phased development project. Please follow the implementation plan and complete phases in order.

## 📄 License

TBD
