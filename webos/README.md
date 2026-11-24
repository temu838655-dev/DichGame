# DichGame webOS receiver (prototype)
Standalone receiver for the existing Android WebSubServer (port 8780). Displays the phone's subtitle page, not an HDMI overlay. Enter the LAN address shown by the phone. No cloud account needed.
For developer testing, generate icon.png (80x80 PNG), then use LG webOS CLI: ares-package webos; ares-install --device <configured-tv> <generated-ipk>; ares-launch --device <configured-tv> com.dichgame.subtitles.
Mobile SSH installer, iOS subtitle server, entitlement checks and real TV validation are not implemented yet. Verify iframe HTTP access on actual webOS versions before release.

### Android installer and pairing
The Android TV setup screen now includes experimental Developer Mode SSH install and launch. The receiver accepts the phone address as a launch parameter, or manually. Enable the TV subtitle server, approve the displayed eight-digit code on Android, and use the saved token on reconnect. This remains a fullscreen receiver, not an HDMI overlay. No physical TV validation or iOS installer yet.
