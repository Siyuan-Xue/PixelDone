# AGENTS.md

This repository-level instruction file is intentionally kept in the PixelDone repository root because OpenAI Codex automatically reads `AGENTS.md` before work. Treat this file as the required entry point for every agent working in this repository.

Official Codex source used for this placement: OpenAI Codex Manual, `Custom instructions with AGENTS.md` (`/codex/guides/agents-md.md`).

## Required Rule Documents

Before editing, building, reviewing, releasing, or creating any Android subproject, read these three documents in this order:

1. `PROJECT_SPEC.md`
2. `PRODUCT_LINE_SPEC.md`
3. `DESIGN_SPEC.md`

Do not rely only on memory or previous sessions. Re-read the three rule documents at the start of each new task unless the current thread has already loaded them after the latest changes.

If any of the three documents cannot be read, stop and report that the project rules are unavailable before making changes.

## What Each Document Controls

- `PROJECT_SPEC.md`: engineering workflow, Android Studio project creation, MVP scope, implementation rules, tests, build verification, APK naming, Git, and release flow.
- `PRODUCT_LINE_SPEC.md`: product-line positioning, product naming, Android package naming, platform scope, cross-product consistency, and developer identity.
- `DESIGN_SPEC.md`: UI/UX, pixel-style visual system, Claude/Anthropic color tokens, layout rules, interaction rules, component rules, and visual QA.

## Codex Surface Boundaries

- `.agents/skills/` contains repository-scoped Codex skills for PixelDone and PixelPark workflows. Use `$design-android-ui-ux` for Android utility UI/UX, Compose screens, responsive layouts, and launcher icon design or review in this repository.
- Copied skills and rule documents must remain repository-relative and cross-platform. Do not add absolute local paths, machine names, user-home paths, or device-specific assumptions.
- User-level Codex rules, if needed, belong outside this repository in the user's Codex configuration.
- Durable project requirements belong in this file and the three standalone rule documents above. Keep `AGENTS.md` short and route detailed rules to the source document.

## High-Priority Repository Rules

- All project rules and durable agent instructions must be written in English.
- PixelDone uses the `Pixel<Name>` product naming pattern and the `com.milesxue.<product>` package naming pattern. New product-line work should keep following that rule unless an existing legacy project explicitly requires otherwise.
- Debug APK release assets must use `{ProjectName}-{versionName}-debug.apk`, for example `PixelDone-2.7.0-debug.apk`.
- Versioned debug APKs must be generated in the same directory as Gradle's default `app-debug.apk`: `app/build/outputs/apk/debug/`.
- Do not create nested release-output directories such as `semantic/debug/`, `pixeldone/`, or project-name folders.
- Preserve the three standalone rule documents as the source of truth. When a rule changes, update the relevant standalone document first, then update this entry file only if the high-level guidance changes.

## Stop Lines

- Do not edit, build, review, release, or create Android project work before reading `PROJECT_SPEC.md`, `PRODUCT_LINE_SPEC.md`, and `DESIGN_SPEC.md`.
- After the user creates a new Android Studio project, do not begin MVP implementation until the Git state and `.gitignore` coverage have been inspected and reported.
- Do not release PixelDone from a parent workspace repository. Enter this repository before Git wrap-up, tagging, pushing, or creating releases.
- Do not consider an APK-output-rule change verified until `assembleDebug` has produced both `app-debug.apk` and `{ProjectName}-{versionName}-debug.apk` in `app/build/outputs/apk/debug/`.

## Verification Expectations

For code changes, run the most relevant tests and build commands from the affected subproject. For APK-output-rule changes, run `assembleDebug` and confirm both files exist in `app/build/outputs/apk/debug/`:

- `app-debug.apk`
- `{ProjectName}-{versionName}-debug.apk`

For documentation-only changes, verify the touched documents are in English and that `AGENTS.md` still points agents to the three rule documents.
