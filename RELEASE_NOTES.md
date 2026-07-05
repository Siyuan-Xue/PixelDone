PixelDone v2.9.3-rc.3 is a debug RC for the refined Dock layout, quick-delete icon, and architecture review.

Highlights:
- Rebalances Dock action distribution so left, center, and right add-button placements use the available Dock width evenly.
- Keeps the center add button geometrically centered while using mirrored action slots for odd button counts.
- Redraws the quick-delete Dock icon with a clearer line-style trash can.
- Splits Dock and update/permission presentation code out of the main route for a cleaner teaching architecture.
- Adds a Google Play readiness review covering permission, privacy, robustness, and Dock function boundaries.

Install note: use the local debug RC APK, `PixelDone-2.9.3-rc.3-debug.apk`, for direct installation or update of `com.milesxue.pixeldone.debug`. Existing `com.milesxue.pixeldone` formal installs remain separate.
