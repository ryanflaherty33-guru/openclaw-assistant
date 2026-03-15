# Building OpenClaw Assistant

This repository can be built locally without access to the release signing key or production Firebase configuration.

## Prerequisites

- JDK 17
- Android SDK Platform 35 and build tools installed
- `local.properties` pointing to your Android SDK if you are not using Android Studio

## Debug builds

1. Copy the checked-in Firebase stub:

   ```bash
   cp app/google-services.json.example app/google-services.json
   ```

2. Disable Firebase at build time when using the stub config:

   ```bash
   FIREBASE_ENABLED=false ./gradlew assembleStandardDebug
   ```

3. Optional variants:

   ```bash
   FIREBASE_ENABLED=false ./gradlew assembleFullDebug
   FIREBASE_ENABLED=false ./gradlew lintStandardDebug
   FIREBASE_ENABLED=false ./gradlew clean testStandardDebugUnitTest
   ```

Notes:

- Debug builds use the normal Android debug keystore. No manual keystore setup is required.
- `standard` excludes VOICEVOX. `full` bundles VOICEVOX runtime assets and is larger.
- The checked-in `google-services.json.example` is for local debug builds only. Do not use it for releases.

## Release builds

Release builds require your own Firebase config, signing key, and a populated `local.properties`.

Required `local.properties` keys:

```properties
storeFile=release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

You also need a real `app/google-services.json` before running:

```bash
./gradlew assembleStandardRelease assembleFullRelease bundleStandardRelease bundleFullRelease
```

The GitHub release workflow uses repository secrets for these values; contributors do not need them for normal OSS development.
