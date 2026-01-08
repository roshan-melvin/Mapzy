# Zwap - Phase 0 Setup Complete ✅

## Package Information

**App Name:** Zwap  
**Package Name:** `com.swapmap.zwap`  
**Project Location:** `/home/rocroshan/Desktop/2026/Ram/DeepBlueS11/zwap`

---

## ✅ Completed Steps

1. ✅ Flutter SDK installed (v3.38.5)
2. ✅ Flutter project created with package name `com.swapmap.zwap`
3. ✅ Package name verified in `android/app/build.gradle.kts`
4. ✅ `.gitignore` configured to protect `key.properties` and keystores

---

## 🔐 Next Steps: SHA-256 Certificate Extraction

### Option 1: Install Android Studio (Recommended)

**Why:** Android Studio includes Android SDK and makes development easier.

**Steps:**
1. Download from: https://developer.android.com/studio
2. Install and open Android Studio
3. Complete SDK setup wizard
4. Open the Zwap project: `/home/rocroshan/Desktop/2026/Ram/DeepBlueS11/zwap`
5. Run `flutter doctor` to verify Android toolchain

**Extract SHA-256 using Android Studio:**
1. Open Android Studio
2. View → Tool Windows → Gradle
3. Navigate to: `zwap → Tasks → android → signingReport`
4. Double-click `signingReport`
5. Check the **Run** tab at bottom
6. Copy the **SHA-256** value from debug variant

---

### Option 2: Install Android Command Line Tools Only

**For minimal setup without Android Studio:**

```bash
# Download Android command line tools
cd ~
mkdir -p android-sdk/cmdline-tools
cd android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest

# Set environment variables (add to ~/.bashrc)
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Install required SDK components
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Accept licenses
flutter doctor --android-licenses

# Verify setup
flutter doctor
```

**Then extract SHA-256:**
```bash
cd /home/rocroshan/Desktop/2026/Ram/DeepBlueS11/zwap/android
./gradlew signingReport
```

Look for output like:
```
Variant: debug
Config: debug
Store: /home/rocroshan/.android/debug.keystore
Alias: androiddebugkey
SHA1: DA:39:A3:EE:5E:6B:4B:0D:32:55:BF:EF:95:60:18:90:AF:D8:07:09
SHA256: E3:B0:C4:42:98:FC:1C:14:9A:FB:F4:C8:99:6F:B9:24:27:AE:41:E4:64:9B:93:4C:A4:95:99:1B:78:52:B8:55
       ↑ COPY THIS VALUE
```

---

### Option 3: Use Keytool Directly (After Debug Build)

**After Android SDK is installed:**

```bash
# Build the app once to generate debug keystore
cd /home/rocroshan/Desktop/2026/Ram/DeepBlueS11/zwap
flutter build apk --debug

# Extract SHA-256
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

---

## 📋 Mappls API Registration

Once you have the SHA-256 certificate:

1. Visit: https://apis.mappls.com/console/
2. Sign up / Log in
3. Create new project → **Android Application**
4. Provide:
   - **Package Name:** `com.swapmap.zwap`
   - **SHA-256 Certificate:** (from above)
5. Submit and receive **Access Token**

---

## 🔒 Secure Token Storage

After receiving the Mappls access token:

**Create `android/key.properties`** (already gitignored):
```properties
mapplsToken=YOUR_MAPPLS_ACCESS_TOKEN_HERE
```

**Update `android/app/build.gradle.kts`:**

Add at the top (after plugins block):
```kotlin
// Load keystore properties
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    // ... existing config ...
    
    defaultConfig {
        // ... existing config ...
        
        // Add Mappls token as manifest placeholder
        manifestPlaceholders["MAPPLS_TOKEN"] = keystoreProperties.getProperty("mapplsToken", "")
    }
}
```

---

## ✅ Verification Checklist

Before proceeding to Phase 1:

- [ ] Android SDK installed (via Android Studio or command line tools)
- [ ] `flutter doctor` shows ✓ for Android toolchain
- [ ] SHA-256 certificate extracted
- [ ] Mappls API project created
- [ ] Access token received
- [ ] Token stored in `android/key.properties`
- [ ] `build.gradle.kts` updated to load token
- [ ] Test build succeeds: `flutter build apk --debug`

---

## 🚀 Ready for Phase 1?

Once all checkboxes above are complete, you're ready to start implementing:
- Map integration with Mappls SDK
- GPS location tracking
- Speed detection
- Navigation features

---

## 📞 Need Help?

**Common Issues:**

**"Android SDK not found"**
→ Install Android Studio or set `ANDROID_HOME` environment variable

**"Keystore not found"**
→ Build the app once: `flutter build apk --debug`

**"SHA-256 not showing"**
→ Make sure you're looking at the **debug** variant, not release

**"Mappls token not working"**
→ Verify SHA-256 matches exactly (no spaces, correct case)
