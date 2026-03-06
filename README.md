# Lifekeeper

Be mindful of your time.

[Download latest APK](https://github.com/nkoexe/lifekeeper/releases)

---

## Local setup

```bash
# Clone
git clone https://github.com/nkoexe/lifekeeper.git
cd lifekeeper

# Build debug APK
./gradlew :app:assembleDebug

# Install on a connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
