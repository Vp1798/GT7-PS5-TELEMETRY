# GT7 Telemetry (Android)

Live Gran Turismo 7 dashboard: supercar-style tach ring, gear, speed, TCS/ASM activity lights, pedals, tyre temps, fuel and laps of fuel left,
current/last/best lap and live delta to your best lap. Own car only: GT7 sends no data about other drivers.

## Use
1. PS5 and phone on the same Wi-Fi (no guest network / AP isolation).
2. PS5: Settings > Network > View Connection Status > note the IP address.
3. App: enter the IP, tap Connect, then start a race or time trial in GT7.
4. Long-press the dashboard (or Back) to return. "How to set up" opens the in-app manual (shown on first launch). "Demo mode" shows fake data.

No console handy? Run `python3 tools/fake_ps5.py` on a computer on the same network and use its IP.

## Build the APK
- GitHub: create a repo, upload this folder, open Actions > Build APK > download `gt7-telemetry-apk`.
- Android Studio: File > Open this folder, Build > Build APK(s). Output: app/build/outputs/apk/debug/app-debug.apk
- Install: copy the APK to the phone and allow "install unknown apps".

## Notes
- Protocol: heartbeat "A" to PS5 UDP 33739; telemetry arrives on UDP 33740, 296 bytes, Salsa20 encrypted.
- Delta needs one completed best lap first; fuel estimate needs one completed lap.

## Custom widgets
Main screen > Import widgets > pick a .json file (see `sample_widgets.json`), or tap Load sample.
Widgets appear on a second page; tap the dashboard to switch pages. Types: number, bar, arc, light, text.
Positions are percentages of the screen. The full list of fields and sources is in the in-app manual.

## Sound and launch screen
A synthesized V8 note follows RPM and throttle (Sound button, or tap the top-right corner of the dashboard).
The launch screen is a top-down finish-line animation with a short engine rev. Requires Android 8.0 or newer.

## About
Developer: [@Vp1798](https://github.com/Vp1798). The in-app About screen links to the GitHub profile.
To show the official GitHub mark instead of the generic code icon, download it from GitHub's logo page and save it as
`app/src/main/res/drawable/github_mark.png` (the app picks it up automatically).
