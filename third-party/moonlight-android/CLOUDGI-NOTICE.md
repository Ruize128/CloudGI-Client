# Vendored Moonlight Android

This is a fixed source snapshot, not a live upstream dependency.

- Upstream: https://github.com/moonlight-stream/moonlight-android
- Release: v12.1
- Commit: 1b1b100e63e8ffcf1496e324ff7b57ee9f2db2a9
- moonlight-common-c: 8af4562af672dd6b9ed28553ead172984fd9a683
- ENet: d3a323fc8b9559786ee059205db74e8027c756f7
- License: GPLv3; see LICENSE.txt and upstream dependency notices.

The repository includes the pinned native submodule contents, so recursive Git checkout is not required.
The `moonlightAndroid` Android library module supplies CloudGI's manifest and build configuration.
The upstream app build.gradle and manifest are retained for reference and are not the build entry point.

Local source adaptations:

1. NvHTTP uses CloudGI's persistent client ID instead of the shared constant and names its pairing session
   `CloudGI-<clientId>`, allowing Host to match both this session name and its network source address.
2. JNI Application.mk targets Android API 26, matching CloudGI's minimum Android version.
3. The mDNS listener has an optional service-found callback used by CloudGI to display unresolved services.
4. Full HTTP request/response logging is disabled, including in debug builds, to keep handshake secrets out of diagnostics.
5. The library wrapper uses the installed NDK 28.2.13676358, Java 17, and arm64-v8a, with the non-root build.

The native stream Activity and its decoder, audio, and input implementation remain upstream code.
CloudGI keeps its own Android application ID and does not replace the official Moonlight application.
The combined distributed Client must comply with the upstream GPLv3 license and third-party licenses.
