# Experimental iPhone Samsung certificate flow

Uses the same Samsung online CA protocol documented by Samsung Electronics in tizen-agent-skills. RSA keys remain in device-only Keychain; account login is hosted by Samsung using a foreground Safari view. The loopback callback requires matching state and expires in five minutes. No account password or token is persisted. Author and Public distributor certificates are checked against pinned public Samsung CA certificates, their keys and DUID before signing the bundled Tizen source. The user confirms the account and target before installation.

Current verification: source implementation only; iOS build, real Samsung OAuth callback, CA response compatibility and TV installation must be tested. A rejected state, certificate or authentication is not bypassed. Signed WGT import remains available. No private key or account secret should be uploaded to GitHub or Cloudflare.

The callback parser checks Host, Origin (when present), state, method and encoding; tests cover malformed escapes, conflicting duplicates and wrong hosts/origins. The IPA workflow checks all bundled TV installer and CA resources. SDB capabilities are read with the uint16 little-endian length prefix rather than treating binary data as text.
