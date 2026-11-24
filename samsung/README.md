# Samsung TV receiver prototype
Target Tizen 3.0+ (2017+), model compatibility unverified. Uses public TVWindow API and HDMI source enumeration. UP opens source menu. Keep console on before launch and disable HDMI-CEC auto-switch if it exits the receiver. Requires signed WGT with Samsung developer certificates for target TV. No mobile installer, 8-digit pairing, or certificate automation yet. Polling uses current Android WebSubServer; cross-origin access must be enabled and tested before integration. Do not distribute unsigned builds as installable.

### Pairing
Enable the TV subtitle server on Android, enter its LAN address on the TV, and approve the eight-digit code in TV setup. Tokens are remembered. Browser access without pairing is opt-in. This is LAN HTTP, not encrypted transport. WGT signing and physical HDMI tests are still required.
