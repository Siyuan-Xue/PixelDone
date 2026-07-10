# UI/UX Design Specification

This document governs visual design, interaction experience, pixel-style components, Claude/Anthropic color usage, layout, and visual quality for all Android subprojects under the current directory.

This document does not define product-line positioning, product naming, developer identity, or package naming rules. Those belong to `PRODUCT_LINE_SPEC.md`. This document also does not define engineering workflow. That belongs to `PROJECT_SPEC.md`.

## 1. Design Principles

### 1.1 Simplicity From Complexity

Each screen should keep only the interactions necessary for the current task. Complex capabilities may exist, but they must be folded, layered, or delayed. Users must not see implementation complexity at first glance.

Design judgment priority:

1. Can the user immediately complete the core task?
2. Can the user understand the current state and next action?
3. Is the visual language unified, restrained, and quiet?
4. Do advanced capabilities appear only when needed?

### 1.2 Tool First

All products are practical utility tools, not marketing-first experiences. After launch, the first screen should be the usable tool itself, not an intro page, promotional page, or decorative hero.

Instructional text is allowed only when it serves the operation itself, such as empty states, error states, permission explanations, or result explanations. Do not add non-functional visual decoration just to show design.

### 1.3 Necessary Controls Visible

Core input, core output, current mode, and primary actions must be clearly visible. Secondary features may use collapsible areas, mode switches, settings pages, or long-press menus, but must not interfere with the MVP main flow.

### 1.4 Pixel Style Is A System Language, Not A Sticker Style

Pixel style comes from geometry, borders, grids, typography, low-noise color, and clear state. It is not large-scale retro decoration. Interfaces should feel like precise, quiet, touchable digital instruments.

## 2. Claude/Anthropic Design Inspiration

This specification references public design cues from the Anthropic official site, Claude product pages, Claude Code, Claude Design, and Claude download pages, and translates them into rules for Android pixel utility products.

External Claude/Anthropic pages and this local specification are both important design sources. The external pages provide product-line inspiration, evolution direction, and aesthetic calibration; this document translates those cues into concrete Android rules, tokens, and QA expectations. Do not ignore the external pages when making design judgments. If a public Claude/Anthropic page and this document appear to diverge in a way that matters, record the source and the difference, then update this specification instead of bypassing it during implementation.

### 2.1 Restrained Typography

Claude's product line uses restrained, clear text and stable spacing. This product line should use short titles, short labels, and short status text. Avoid exaggerated slogans and excessive explanation.

### 2.2 Warm Neutral Colors

Claude/Anthropic's public color system is built around warm white, oat, slate black, clay orange, and low-saturation auxiliary colors. This product line should avoid high-saturation neon, pure-blue tech styling, glassmorphism, and large gradients.

### 2.3 Workflow First

Claude Code emphasizes completing tasks within the user's existing workflow. Claude Desktop / Download emphasizes continuity across devices. Claude Design emphasizes moving from idea to draft to refinement and delivery.

Translated to this product line:

- Tools enter the task immediately after launch.
- Input, calculation, save, copy, share, and similar actions should match the user's natural flow.
- Advanced features must not interrupt the basic flow.
- Phone and tablet share one interaction model, with only layout density adjusted.

## 3. Specification Priority

When an existing project implementation conflicts with this specification, this specification wins.

Priority from high to low:

1. Public Claude/Anthropic design cues, official color tokens, and this document's Android translation of those cues.
2. Pixel utility design principles and component rules in this document.
3. Product-line consistency constraints in `PRODUCT_LINE_SPEC.md`.
4. Historical implementation of a specific project.

Historical projects are references for current state and migration cost only. They are not design authorities and must not override the external Claude/Anthropic sources or this specification.

## 4. Color System

### 4.1 Canonical Claude Tokens

Future new projects should prefer the following public Claude/Anthropic color tokens. The values come from public webpage style resource research and should be treated as the current product line's canonical palette.

| Token | Hex | Use |
| --- | --- | --- |
| `clay` | `#D97757` | Primary actions, selected state, brand emphasis |
| `clayInteractive` | `#C96442` | Pressed state, hover, darker primary action |
| `ivory` | `#FAF9F5` | Light background, highlighted text |
| `ivoryMedium` | `#F0EEE6` | Light secondary surface |
| `ivoryDark` | `#E8E6DC` | Light border, light panel |
| `oat` | `#E3DACC` | Warm neutral background |
| `slateDark` | `#141413` | Dark background, inverse primary text |
| `slateMedium` | `#3D3D3A` | Dark panel, border |
| `slateLight` | `#5E5D59` | Secondary text, weak border |

### 4.2 Claude Gray Scale

| Token | Hex |
| --- | --- |
| `gray050` | `#FAF9F5` |
| `gray100` | `#F5F4ED` |
| `gray150` | `#F0EEE6` |
| `gray200` | `#E8E6DC` |
| `gray250` | `#DEDCD1` |
| `gray300` | `#D1CFC5` |
| `gray350` | `#C2C0B6` |
| `gray400` | `#B0AEA5` |
| `gray450` | `#9C9A92` |
| `gray500` | `#87867F` |
| `gray550` | `#73726C` |
| `gray600` | `#5E5D59` |
| `gray650` | `#4D4C48` |
| `gray700` | `#3D3D3A` |
| `gray750` | `#30302E` |
| `gray800` | `#262624` |
| `gray850` | `#1F1E1D` |
| `gray900` | `#1A1918` |
| `gray950` | `#141413` |
| `gray1000` | `#000000` |

### 4.3 Auxiliary Tokens

Auxiliary colors must be used sparingly, only to express state, category, or a small number of functional differences.

| Token | Hex | Use |
| --- | --- | --- |
| `mineral` | `#629987` | Success, completion, stable state |
| `olive` | `#788C5D` | Secondary success, natural/available state |
| `sky` | `#6A9BCC` | Information, links, non-destructive emphasis |
| `fig` | `#C46686` | Special category, cautious emphasis |
| `cactus` | `#BCD1CA` | Light success background |
| `coral` | `#EBCECE` | Light error background |

Default error color is `#DF6666`. Destructive actions must also rely on text or icons, not color alone.

### 4.4 Approved Google Status Tokens

The following Google identity colors are approved status tokens only when a task explicitly requests or approves them. They come from Google's public identity material and the Google-hosted logo SVG.

| Token | Hex | Use |
| --- | --- | --- |
| `googleBlue` | `#4285F4` | Approved status display, PixelDone `mid` priority |
| `googleRed` | `#EA4335` | Approved high-alert status display, PixelDone `xhigh` priority |
| `googleYellow` | `#FBBC05` | Approved warning status display, PixelDone `high` priority |
| `googleGreen` | `#34A853` | Approved stable or low-urgency status display, PixelDone `low` priority |

Usage rules:

- Keep Claude/Anthropic tokens as the default product-line palette.
- Use Google status tokens only for status, category, or priority display inside app pages when explicitly approved or requested.
- Do not use Google status tokens as default page backgrounds, primary action systems, broad decorative fills, or replacements for the product-line palette.
- Pair these colors with labels, icons, text, or layout state. Do not rely on color alone.
- For approved priority displays, map `low` to `googleGreen`, `mid` to `googleBlue`, `high` to `googleYellow`, and `xhigh` to `googleRed`.

### 4.5 Android Compose Token Recommendation

New projects should define semantic tokens in `ui/theme/Color.kt` instead of scattering hex values through screens.

```kotlin
val ClaudeClay = Color(0xFFD97757)
val ClaudeClayInteractive = Color(0xFFC96442)
val ClaudeIvory = Color(0xFFFAF9F5)
val ClaudeOat = Color(0xFFE3DACC)
val ClaudeSlateDark = Color(0xFF141413)
val ClaudeGray700 = Color(0xFF3D3D3A)
val ClaudeGray600 = Color(0xFF5E5D59)
```

Recommended dark tool theme:

- `background`: `gray950`
- `surface`: `gray850` or `gray800`
- `surfaceVariant`: `gray750`
- `primary`: `clay`
- `onPrimary`: `gray950`
- `onSurface`: `gray050`
- `outline`: `gray600`

Recommended light tool theme:

- `background`: `ivory`
- `surface`: `gray100`
- `surfaceVariant`: `oat`
- `primary`: `clay`
- `onPrimary`: `gray950`
- `onSurface`: `gray950`
- `outline`: `gray300`

### 4.6 Legacy Note

Existing project palettes may be used as historical compatibility references during migration. New work should use the canonical Claude/Anthropic tokens above unless `DESIGN_SPEC.md` is intentionally updated first.

## 5. Pixel Component Rules

### 5.1 Shape

- Use rectangles or near-rectangles by default.
- Default corner radius is `0dp`.
- If softening is truly needed, maximum corner radius is `4dp`.
- Do not use large rounded cards, pill buttons, glassmorphism, or floating shadow cards.

### 5.2 Borders

- Main panel border: `2dp`.
- Secondary dividers and internal grid lines: `1dp`.
- Border colors should prefer `gray600`, `gray700`, or `slateLight`.
- Selected states may use `clay` or `clayInteractive` as border emphasis.

### 5.3 Spacing

Use a 4dp grid:

- Micro spacing: `4dp`
- Component internal spacing: `8dp`
- Component-to-component spacing: `12dp`
- Phone page margin: `16dp` or `20dp`
- Tablet page margin: `24dp` or `32dp`

Spacing must be stable and must not jump because of button text, state changes, or result length.

### 5.4 Typography

- Tool UI uses `FontFamily.Monospace` by default.
- Main weights are `Normal`, `SemiBold`, and `Bold`.
- `letterSpacing` is fixed at `0.sp`.
- Do not scale font size with viewport width.
- Long text must wrap or truncate; it must not overflow buttons, panels, or input cells.

Recommended sizes:

| Context | Size |
| --- | --- |
| Page title | `18sp` to `22sp` |
| Status text | `12sp` to `14sp` |
| Body / label | `14sp` to `16sp` |
| Primary button | `16sp` to `20sp` |
| Instrument / calculation result | `32sp` to `48sp`, constrained by available space |

## 6. Layout Rules

### 6.1 Phone

- Single column by default.
- Main output area near the top, main input area near the bottom.
- Mode switches near the title or main tool area.
- Key actions must be reachable during one-handed use.
- Do not use drawer navigation as the default structure for small utility tools.

### 6.2 Tablet

Tablet layout must not simply stretch the phone UI wider. Prefer:

- Two-pane layout: input on the left, preview or result on the right.
- Master-detail layout: main tool area plus history/settings/batch area.
- Higher-density grid: keep component sizes stable and do not let buttons grow endlessly.

### 6.3 Responsive Stability

- Fixed-format controls must have stable size constraints, such as `aspectRatio`, `weight`, `heightIn`, or `widthIn`.
- Dynamic results must use `maxLines`, `overflow`, or scrollable containers.
- Status text changes must not squeeze the core tool area.

## 7. Interaction Rules

### 7.1 First-Screen Behavior

After launch, the app should enter the core tool directly. Welcome pages, onboarding pages, and permission pages are allowed only when functionally necessary.

### 7.2 Mode Switching

When a tool has multiple work modes, use a pixel-style segmented control. If there are more than 3 modes, review whether the product is overloaded.

### 7.3 Input

Input controls must match task semantics:

- Numeric tools use numeric keyboards, steppers, sliders, or short text inputs.
- Boolean settings use switches or checkboxes.
- Small option sets use segmented controls.
- Large option sets use dropdowns or separate selection pages.

### 7.4 Feedback

Every user action must produce at least one perceptible feedback signal:

- Button pressed-state color change.
- Result area update.
- Short status-bar text.
- Error border or error text.

Feedback should be brief and clear. Do not use exaggerated animation.

### 7.5 Motion

Use little motion by default. Allowed:

- `100ms` to `180ms` pressed-state and toggle-state transitions.
- Simple alpha or position transitions.
- Slight emphasis when results update.

Forbidden:

- Large parallax scrolling.
- Decorative looping animation.
- Particles, light spots, or flowing gradients unrelated to the tool task.

## 8. Component Rules

### 8.1 Header

The header should only carry product name, current state, or one necessary entry point. Keep it low in height and avoid turning it into a brand display area.

### 8.2 Display Panel

Results, previews, counts, status, and other main outputs should use pixel display panels:

- Background uses `gray100`, `oat`, or an inverted dark panel.
- Border is `2dp`.
- Content is right-aligned or aligned according to domain semantics.
- Error state must change color and provide short text.

### 8.3 Pixel Button

Buttons have three categories:

- Primary: core submit, calculate, or save actions; use `clay`.
- Secondary: modes, operators, or secondary actions; use dark surface or `gray750`.
- Destructive: clear, delete, or reset; use darker clay or an error-color border.

Button text must be short. Prefer symbols when they can express the action without sacrificing comprehension.

### 8.4 Setting Row

Each setting row must express one intention. Settings pages must not become feature piles. Every setting must explain what behavior it changes.

### 8.5 Empty State

Empty states should only tell the user the next action, not explain product philosophy.

Examples:

- Recommended: `Enter a value to see the result`
- Not recommended: `Welcome to a powerful smart tool`

## 9. App Icon Rules

Launcher icons are part of the product-line visual system. They must feel like quiet, precise PixelPark utility tools, not default Android Studio assets, generic logo marks, or decorative illustrations.

### 9.1 Platform Baseline

Android launcher icons must follow the adaptive icon model:

- Color icons use separate foreground and background layers.
- Each adaptive icon layer is `108dp x 108dp`.
- The primary subject must stay inside the safe visual viewport.
- Provide a monochrome layer when feasible so themed app icons can work cleanly.

Google Play / store-style source assets should be prepared as a full square image, with platform masking and shadows left to the launcher or store surface.

### 9.2 Subject Size

The icon subject is the main symbolic mark users recognize at launcher size. Measure its tight bounding box, excluding the full-bleed background.

- Target subject size: `60dp` on the longest side inside a `108dp` adaptive icon layer.
- Acceptable range: `52dp` to `66dp` on the longest side.
- Hard maximum: `66dp`; do not exceed the adaptive icon safe viewport.
- For `512px x 512px` Play / store-style assets, use the same ratio: target about `284px`, acceptable range about `246px` to `313px`.

Use the lower end of the range for dense or detailed subjects. Use the upper end only for very simple geometric marks with clear negative space.

### 9.3 Contrast And Color

Use a strong subject/background value contrast:

- If the icon subject is dark, use a light background.
- If the icon subject is light, use a dark background.
- Prefer `gray950`, `gray850`, `ivory`, `gray100`, or `oat` for the main subject/background relationship.
- Use `clay` only as a restrained accent, not as a large high-noise fill unless the icon needs primary-action emphasis.

The icon should remain legible at small launcher sizes and in grayscale-like visual scans. Do not rely on color alone to distinguish the subject.

### 9.4 Composition

App icons should use one clear symbolic subject tied to the tool's core task.

- Use simple pixel-style geometry, clean edges, and stable proportions.
- Keep the background full-bleed and visually quiet.
- Avoid tiny text, slogans, initials that require reading, multi-object scenes, photo-like detail, busy patterns, and decorative retro stickers.
- Do not add outer shadows, launcher masks, rounded-corner frames, or platform effects that Android launchers or Google Play already apply.
- Keep icons recognizably related across the product line without forcing every product to share the same symbol.

### 9.5 Reference Calibration

Use Android adaptive icon requirements and Google Play icon specifications as hard platform constraints. Use Apple, Google, X.com, Anthropic / Claude, and OpenAI brand practices as calibration for restraint: simple marks, clear hierarchy, generous negative space, and protected proportions.

## 10. Localization And Bidirectional Layout

- Layouts must expand for translated text without clipping, overlap, unreachable controls, or fixed-width assumptions. Prefer flexible rows, wrapping, and content-driven height.
- Arabic must be verified in real RTL layout direction. Directional icons, row order, padding, alignment, and navigation affordances must mirror when their meaning is directional; non-directional product marks and media must not be mirrored.
- Keep touch targets at least `44dp` in every locale. Do not shrink type below the product typography baseline to force a translation into a fixed control.
- Use localized plurals and date/time/number formatting. Avoid manual uppercase transformations for scripts where casing is not meaningful.
- Accessibility descriptions, state descriptions, semantic action names, notifications, channels, alarms, dialogs, empty states, auth, sync, and update UI must receive the same locale and RTL QA as visible screen text.
- Visual QA must cover English, the longest supported Latin/Cyrillic labels, Simplified Chinese, and Arabic RTL on at least one phone viewport. Tablet QA is required when the product supports tablets.

## 11. Visual QA Checklist

Before release, every project must check:

- Is the first screen directly usable?
- Is there unnecessary explanatory text?
- Are there large radii, shadows, gradients, glassmorphism, or other elements that deviate from the pixel utility language?
- Do colors come from this specification's tokens?
- Does the launcher icon use a non-default PixelPark subject, correct contrast, and the `52dp` to `66dp` adaptive-icon subject range?
- Do phone and tablet layouts avoid overlap, overflow, and unreachable click areas?
- Do long text, long results, and error messages avoid breaking layout?
- Does every clickable element have clear feedback?
- If both dark and light themes exist, are semantic colors consistent?
- Do all declared languages render without fallback English caused by a missing resource?
- Does Arabic RTL preserve hierarchy, touch targets, and intuitive directional behavior?

## 12. Sources

- [Anthropic official site](https://www.anthropic.com/)
- [Claude Code product page](https://claude.com/product/claude-code)
- [Claude Design product page](https://claude.com/product/claude-design)
- [Claude download page](https://claude.com/download)
- [Anthropic public CSS token](https://cdn.prod.website-files.com/67ce28cfec624e2b733f8a52/css/ant-brand.shared.99b3c3efd.min.css)
- [Claude public CSS token](https://cdn.prod.website-files.com/6889473510b50328dbb70ae6/css/claude-brand.shared.68b0f01f285d6d66e1484490.ae11f7266.min.css)
- [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Google Play icon design specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
- [Apple Human Interface Guidelines: App icons](https://developer.apple.com/design/human-interface-guidelines/app-icons)
- [Google Brand Resource Center](https://about.google/brand-resource-center/)
- [Google Design: Evolving the Google Identity](https://design.google/library/evolving-google-identity)
- [Google-hosted logo SVG asset](https://www.gstatic.com/images/branding/googlelogo/svg/googlelogo_clr_74x24px.svg)
- [X.com Brand Toolkit](https://about.x.com/en/who-we-are/brand-toolkit)
- [OpenAI Design Guidelines](https://openai.com/brand/)
