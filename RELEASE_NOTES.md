PixelDone v2.9.3-rc.4 is a debug RC for restored anchored Dock spacing, the quick-delete icon, and architecture review.

Highlights:
- Restores Dock action spacing to the anchored layout where function buttons keep fixed gaps from the `+` button.
- Keeps centered odd Dock button counts weighted to the left side, matching the earlier Dock interaction model.
- Redraws the quick-delete Dock icon with a clearer line-style trash can.
- Splits Dock and update/permission presentation code out of the main route for a cleaner teaching architecture.
- Adds a Google Play readiness review covering permission, privacy, robustness, and Dock function boundaries.

Install note: use the local debug RC APK, `PixelDone-2.9.3-rc.4-debug.apk`, for direct installation or update of `com.milesxue.pixeldone.debug`. Existing `com.milesxue.pixeldone` formal installs remain separate.
