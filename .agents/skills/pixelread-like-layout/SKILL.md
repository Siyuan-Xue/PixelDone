---
name: pixelread-like-layout
description: Align PixelDone Android Compose screens to the PixelPark page-shell standard without requiring a sibling PixelRead checkout. Use when refining UI layout spacing, top/bottom padding, product identity placement and size, header/status/tool panel spacing, stable 4dp-grid constraints, or PixelPark-like app chrome without replacing PixelDone-specific core content.
---

# PixelPark Page Shell Layout

## Overview

Align PixelDone's outer Compose page shell to the PixelPark page-shell standard while preserving PixelDone's own core workflow. Treat `DESIGN_SPEC.md` as the design authority. This standalone copy does not require a sibling PixelRead repository.

## Required Sources

Before editing UI, read these Android workspace files in order:

1. `PROJECT_SPEC.md`
2. `PRODUCT_LINE_SPEC.md`
3. `DESIGN_SPEC.md`

Then inspect PixelDone's current Compose shell, route, component, and theme files. If a historical PixelRead-style note conflicts with `DESIGN_SPEC.md`, follow `DESIGN_SPEC.md` and mention the difference.

## Alignment Workflow

1. Identify PixelDone's main Compose entry point, top-level screen, theme tokens, and app-specific core content.
2. Compare only the outer shell against the PixelPark page-shell standard: page padding, status/header row, main panel spacing, tool/action panel spacing, footer identity, borders, stable dimensions, and overflow behavior.
3. Preserve PixelDone's domain content, labels, actions, state model, result surfaces, and necessary empty/error states. Do not import reader-specific text, book actions, document logic, or icons.
4. Apply the smallest UI changes needed to make PixelDone feel consistent with PixelPark. Avoid broad architecture refactors, new settings, new navigation, icon changes, or release work unless the user separately asks for them.
5. Verify with the affected app's relevant build or UI checks when code changes are made, and inspect the rendered screen when a runnable app or screenshot is available.

## Page Shell Standard

Use this PixelPark page-shell standard for these areas:

- Outer page: keep the first screen directly usable, with `fillMaxSize`, product background, system bar padding, restrained top padding, and no decorative hero or landing content.
- Top and bottom padding: keep a compact top entry and low footer placement. Keep padding stable across status changes and keyboard-free states.
- Product identity: place the product name and `CODEX & XUE` identity with restraint, typically in a small footer/status area. Do not make identity a large brand block.
- Header/status row: keep it low, bordered, monospace, and state-focused. Use short status text, stable height, `maxLines`, and ellipsis so status changes do not resize the shell.
- Main panel and tool spacing: keep the main tool panel prominent, bordered, rectangular, and centered within sensible max widths. Keep header-to-panel, panel-internal, and action spacing stable while preserving PixelDone's workflow.
- Stable constraints: use the 4dp grid from `DESIGN_SPEC.md`; prefer explicit `height`, `heightIn`, `widthIn`, `sizeIn`, `weight`, `maxLines`, `overflow`, and scroll containers for fixed-format controls and dynamic results.
- Pixel language: keep rectangular geometry, `0dp` radius by default, `4dp` maximum only when needed, `2dp` main borders, `1dp` internal dividers, Claude/Anthropic tokens, monospace typography, and `letterSpacing = 0.sp`.

## Guardrails

- Do not let historical PixelRead implementation details override `DESIGN_SPEC.md`.
- Do not force PixelDone into a reader layout. Align the shell, spacing, and identity treatment while keeping PixelDone's task workflow app-specific.
- Do not edit launcher icons, release files, Git metadata, or unrelated app code for a layout-only request.
- Do not introduce non-Android-native UI frameworks or decorative visual systems.
- Report any intentional deviation from this page-shell standard or `DESIGN_SPEC.md` in the final response.

## Delivery Notes

In the final response, summarize the layout areas changed, the local PixelDone files checked, the verification run, and any app-specific content deliberately left unchanged.
