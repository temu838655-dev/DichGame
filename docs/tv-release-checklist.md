# TV release gates
- Android TV: verify pairing, saved token, overlay permission, HDMI visibility, reconnect and audio on physical TV.
- Samsung: signed WGT per Samsung certificate requirements; verify TVWindow permissions, HDMI source selection, subtitle polling, remote UP, CEC, HDR and game mode on actual models. Receiver now uses eight-digit approval and remembered tokens; verify expiry, revocation, reconnect and CORS on hardware. Legacy browser access is separately enabled. LAN HTTP is not encrypted. Do not expose port 8780 to the internet.
- LG: package receiver with icon, verify installation and launch, confirm HDMI behavior separately. Fullscreen receiver is not an HDMI overlay.
- Android/iOS installers: require tested developer protocol, device identity verification, secure credential storage, signed package and actual install/launch confirmation. No fake success.
- iOS: implement subtitle producer and TV transport before advertising live TV support.
- No release claim until build and hardware tests pass.
