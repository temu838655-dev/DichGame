# TV release gates
- Android TV: verify pairing, saved token, overlay permission, HDMI visibility, reconnect and audio on physical TV.
- Samsung: signed WGT per Samsung certificate requirements; verify TVWindow permissions, HDMI source selection, subtitle polling, remote UP, CEC, HDR and game mode on actual models. Receiver now uses eight-digit approval and remembered tokens; verify expiry, revocation, reconnect and CORS on hardware. Legacy browser access is separately enabled. LAN HTTP is not encrypted. Do not expose port 8780 to the internet.
- LG: package receiver with icon, verify installation and launch, confirm HDMI behavior separately. Fullscreen receiver is not an HDMI overlay.
- Android/iOS installers: require tested developer protocol, device identity verification, secure credential storage, signed package and actual install/launch confirmation. No fake success.
- iOS: screenshot translations can be sent to the Android TV receiver after eight-digit pairing. Verify local-network permission, ATS, voice and clearing on real devices. Foreground iOS subtitle server can also serve paired LG/Samsung receivers with screenshot results; verify on real devices. No live screen capture or direct LG/Samsung installer yet.
- No release claim until build and hardware tests pass.

- iPhone Samsung installer (experimental branch): signed WGT document import, target IP confirmation, SDB transfer and install result parsing. Verify build, real-device authorization, TV certificate and successful HDMI window before release. Does not issue certificates.

- iPhone LG installer experimental: pinned CSSH2 source dependency, bounded key download, explicit SHA256 trust before authentication, bundled IPK, byte count and Luna install/launch verification. Review dependency security and test on actual TV before release.

- iPhone to Android TV: explicit APK import, bounded ZIP structure check, temporary LAN URL with random token, HEAD/GET and user-confirmed TV installation. No silent install. Verify download, expiry, stop and foreground-only operation on hardware.
