PixelDone v1.3.1 hardens automatic update checks for unreliable network conditions.

Highlights:
- Keeps startup update checks quiet when the device is offline or GitHub times out.
- Preserves coroutine cancellation while still treating ordinary network failures as unavailable updates.
- Clears the manual `OFFLINE` update status after a short delay.
- Adds unit coverage for update-check timeout and cancellation behavior.
- Keeps the v1.3 automatic update dot and `Don't show again` dialog behavior unchanged.
- Adds no new permissions, dependencies, or settings screen.

Install note: if you still have the old `com.codexue.pixeldone` prototype build, uninstall it before installing this private release.

The attached `PixelDone-1.3.1-release.apk` asset is the signed private release APK for direct installation and app-internal updates.

CODEX & XUE
