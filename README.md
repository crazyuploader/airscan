# Airscan

[![Build and test](https://github.com/crazyuploader/airscan/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/crazyuploader/airscan/actions/workflows/build-and-test.yml)
[![Latest release](https://img.shields.io/github/v/release/crazyuploader/airscan)](https://github.com/crazyuploader/airscan/releases/latest)
[![License: Apache-2.0](https://img.shields.io/github/license/crazyuploader/airscan)](LICENSE)

An Android app that shows Wi-Fi and mobile signal on your phone. It is free, has no ads, no account and no tracking, and needs no Google services.

Airscan is a personal project and is not on the Play Store. It is not affiliated with any other signal app.

## Features

Airscan has five tabs:

- Mobile: live signal for each SIM. It shows technology, RSRP, RSRQ, SINR, RSSI, cell identity, band and 5G mode (SA or NSA), plus neighbour cells.
- Wi-Fi: the connected network, nearby access points with scan age, and a channel graph for 2.4, 5 and 6 GHz.
- Meter: a big gauge and a two-minute graph for walking around. Pick Wi-Fi or a SIM.
- History: a signal graph, and recording to CSV and GPX files that you can share.
- Access: what Android lets the app read, and how to allow more.

## Limits

- Android does not give normal apps carrier aggregation, MIMO layers or QAM. Airscan does not show them.
- Some modems report no CQI or timing advance. Those fields show "n/a".
- Neighbour cells cannot be tied to one SIM.
- It needs precise location. Android hides cell and Wi-Fi details without it. Airscan never sends data off the phone.

## Install

Download the APK from [Releases](https://github.com/crazyuploader/airscan/releases) and install it:

```bash
adb install --user 0 airscan-<version>.apk
```

It needs Android 12 (API 31) or newer. It asks for precise location and phone state.

## Build

You need JDK 21 and the Android SDK, or only Docker:

```bash
./gradlew build                  # with a local JDK and SDK
tools/docker-build.sh build      # in Docker, nothing else installed
```

The debug APK is in `app/build/outputs/apk/debug/`. The Docker script makes a debug key in `.debug-keystore/` (ignored by Git), so debug builds update in place.

Before you send a change, run the same checks as CI:

```bash
./gradlew spotlessApply lintDebug testDebugUnitTest
```

## Release

1. Set `versionCode` and `versionName` in `app/build.gradle.kts`, and add the changes to `CHANGELOG.md`.
2. Push a tag that matches `versionName`, for example `git tag v0.1.0 && git push origin v0.1.0`.

The "Publish signed release" workflow tests the code, builds the APK, signs it, checks the signing certificate, and attaches the APK and its SHA-256 file to a GitHub Release. It needs these repository secrets and variables:

| Name | Kind | Value |
| --- | --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Secret | The keystore file, base64 encoded |
| `SIGNING_STORE_PASSWORD` | Secret | The keystore password |
| `SIGNING_KEY_PASSWORD` | Secret | The key password |
| `SIGNING_KEY_ALIAS` | Variable | The key alias. Not a secret: GitHub masks secret values in logs, and the alias is the project name |
| `EXPECTED_CERT_SHA256` | Variable | SHA-256 of the signing certificate. The job fails if the APK is signed with another key |

To sign a release build on your computer, create `keystore.properties` in the repository root. Git ignores it.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without a key, `assembleRelease` builds an unsigned APK.

## License

[Apache License 2.0](LICENSE)
