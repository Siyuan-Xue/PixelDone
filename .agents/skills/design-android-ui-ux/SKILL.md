---
name: design-android-ui-ux
description: Design and review UI/UX for PixelDone in this standalone Android repository. Use when Codex is asked to create, refine, audit, or implement Jetpack Compose screens, interaction flows, visual systems, responsive phone/tablet layouts, launcher icons, app icons, design QA, or UI-focused code changes.
---

# Design Android UI/UX

## Overview

Act as the UI/UX designer for this Android product line. Keep designs practical, pixel-styled, quiet, fast, and directly usable as mobile/tablet utility tools.

## Required Source Reading

Before designing, reviewing, or editing UI, read these repository documents in order from the PixelDone repository root:

1. `PROJECT_SPEC.md`
2. `PRODUCT_LINE_SPEC.md`
3. `DESIGN_SPEC.md`

Stop and report the missing source if any file cannot be read. Treat those documents as the source of truth; do not duplicate or override them in this skill.

## Design Workflow

1. Identify the product name, package, core idea, target users, MVP workflow, and current project state.
2. Inspect the relevant Compose screens, theme files, resources, app icon assets, and README if they exist.
3. Design the first screen as the usable tool itself, not a marketing page, onboarding page, or decorative hero.
4. Preserve one clear core path: visible input, visible output/result, current state, primary action, necessary errors, and a verifiable success state.
5. Apply the product-line identity with restraint: use `CODEX x XUE` in UI when appropriate and `CODEX & XUE` in docs or metadata.
6. Adapt layouts for phone and tablet: single-column phone flow, and tablet layouts that add useful space without stretching controls endlessly.
7. Verify visual quality by checking actual rendered UI whenever a runnable app or screenshot path is available.

## Visual Rules

Use the Claude/Anthropic-inspired tokens from `DESIGN_SPEC.md`. Prefer semantic Compose colors in `ui/theme/Color.kt` and avoid scattered hex values.

Keep Claude/Anthropic tokens as the default product-line palette. Use approved Google status tokens only when explicitly requested or approved for status, category, or priority display: `googleBlue` `#4285F4`, `googleRed` `#EA4335`, `googleYellow` `#FBBC05`, and `googleGreen` `#34A853`. For approved priority displays, map `LOW` to green, `MID` to blue, `HIGH` to yellow, and `XHIGH` to red. Pair these colors with labels, icons, or text, and do not use them for page backgrounds, primary action systems, or broad decorative fills.

Use pixel-style geometry:

- Prefer rectangles or near-rectangles.
- Use `0dp` corner radius by default and `4dp` maximum only when softening is necessary.
- Use `2dp` main panel borders and `1dp` internal dividers.
- Use a 4dp spacing grid.
- Prefer `FontFamily.Monospace`, stable sizes, and `letterSpacing = 0.sp`.

Avoid large rounded cards, pill-heavy controls, shadows, glassmorphism, decorative gradients, looping decorative motion, pure-blue tech styling, neon colors, and retro decoration that does not serve the task.

## App Icon Rules

Use `DESIGN_SPEC.md` as the source of truth for PixelPark launcher icon rules. When designing or reviewing app icons:

- Inspect existing launcher foreground, background, monochrome, mipmap, and manifest icon resources.
- Apply clear contrast: dark subject on light background, or light subject on dark background.
- Keep the adaptive icon subject within `52dp` to `66dp` on its longest side; target `60dp`.
- Avoid tiny text, multi-object scenes, busy decoration, launcher shadows, masks, and platform effects.
- Verify the packaged icon is a lightweight custom PixelPark icon, not the Android Studio default.

## Interaction Rules

Choose controls by task semantics:

- Use numeric keyboards, steppers, sliders, or compact text inputs for numeric tools.
- Use switches or checkboxes for booleans.
- Use segmented controls for small mode sets.
- Use dropdowns or separate selection pages for larger option sets.
- Keep mode counts small; if more than 3 modes appear, review whether the product is overloaded.

Make every action produce clear feedback through pressed states, result updates, short status text, or error states. Use motion sparingly, typically `100ms` to `180ms`.

## Compose Implementation Guidance

Prefer Jetpack Compose and Material 3. Keep composables small and direct, and extract reusable components only after real reuse exists.

Separate business logic from UI. Keep parsing, calculation, conversion, validation, and result generation in pure Kotlin code with focused unit tests.

Use stable constraints for fixed-format UI: `aspectRatio`, `heightIn`, `widthIn`, `weight`, `maxLines`, `overflow`, and scroll containers where needed. Long labels, long results, and errors must not resize or break the tool surface.

## Review Checklist

Before delivery, check:

- The first screen is immediately usable.
- The MVP path is clear within 10 seconds.
- Phone and tablet layouts avoid overlap, overflow, unreachable controls, and endless stretching.
- Colors, typography, borders, shape, spacing, and motion follow `DESIGN_SPEC.md`.
- Every clickable control has clear feedback.
- Error, empty, loading, and success states are present when relevant.
- The app icon is lightweight and not the default Android icon when packaging an MVP.
- The UI does not add settings, themes, accounts, sync, history, or architecture beyond the current validated need.
