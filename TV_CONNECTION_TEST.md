# TV Connection Test Report

Updated: 2026-11-26 (UTC+8)

## Protocol observed in source
- Android TV receiver listens on TCP 8765.
- UDP discovery listens on 8766 and responds to DICHGAME? with DICHGAME:8765.
- Pairing uses an 8-digit code and returns a random token.
- Subtitle and PCM audio requests carry the token in X-Token.
- TV subtitle text is decoded as UTF-8; Android TextView is used for display.

## Changes on branch fix/tv-receiver-resilience-2026-11-26
- Bounded client executor: 2 core / 4 max workers and queue capacity 32.
- Overload rejects and closes excess sockets instead of creating unbounded threads.
- TCP listener retries after transient socket/server exceptions.
- UDP discovery retries after transient socket exceptions.
- Service shutdown sets a stop flag, closes listener/discovery sockets and shuts down client workers.

## Test status
| Test | Status |
|---|---|
| Kotlin compile / unit tests | Awaiting GitHub Actions for this branch |
| TCP pairing on physical Android TV | Not tested |
| UDP discovery across router/AP isolation | Not tested |
| Subtitle update latency / ordering | Not tested |
| Disconnect and reconnect during gameplay | Not tested |
| PCM audio transfer and TTS | Not tested |
| LG webOS / Samsung Tizen installation and overlay | Not claimed; platform-specific limitations remain |

## Manual validation needed
Test same Wi-Fi and guest Wi-Fi separately; verify client isolation, firewall, router multicast/broadcast behavior, TV sleep/wake, phone network changes, invalid pairing code rate limiting, and reconnection after receiver process restart. Never describe a Samsung/LG overlay as supported unless verified on the target model and firmware.
