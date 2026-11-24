# Samsung TV receiver prototype
Target Tizen 3.0+ (2017+), model compatibility unverified. Uses public TVWindow API and HDMI source enumeration. UP opens source menu. Keep console on before launch and disable HDMI-CEC auto-switch if it exits the receiver. Requires signed WGT with Samsung developer certificates for target TV. No mobile installer, 8-digit pairing, or certificate automation yet. Polling uses current Android WebSubServer; cross-origin access must be enabled and tested before integration. Do not distribute unsigned builds as installable.

### Pairing
Enable the TV subtitle server on Android, enter its LAN address on the TV, and approve the eight-digit code in TV setup. Tokens are remembered. Browser access without pairing is opt-in. This is LAN HTTP, not encrypted transport. WGT signing and physical HDMI tests are still required.

### Developer deployment (computer fallback)
Create a Samsung certificate profile for the intended TV DUID, package with `package-dev.sh` or `package-dev.ps1`, and connect in Developer Mode with `sdb connect TV_IP`. Read the exact serial in `sdb devices`. Run `bash install-dev.sh TV_SERIAL dist/FILE.wgt` or `powershell -File install-dev.ps1 -Serial TV_SERIAL -Package dist/FILE.wgt`. These scripts validate the intended app and signature files, then invoke the official CLI to install and launch; signature validity is enforced by the TV. This is not a mobile installer. References: https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/command-line-interface.html and https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html .

### Source export
Run `python3 samsung/export-project.py dist/DichGame-Samsung-source.zip` or download the `DichGame-Samsung-unsigned-source` CI artifact. The Android installer also exports this exact unsigned project. Extract, remove SIGNING-REQUIRED.txt, then sign with your local Samsung certificate profile. Never rename this ZIP to WGT or treat it as a signed package.
