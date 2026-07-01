# Product Line Specification

This document governs PixelDone's product-line positioning, brand consistency, naming, platform scope, developer identity, and cross-product rules within this standalone repository.

This document is the highest-level entry point for brand and product-line constraints. Visual details are defined in `DESIGN_SPEC.md`; engineering workflow is defined in `PROJECT_SPEC.md`.

## 1. Product-Line Positioning

This product line is a family of pixel-style Android utility tools for phones and tablets.

The products should feel like tools from the same source: light, precise, fast, quiet, and orderly. They do not aim to be large all-in-one apps. Each one uses minimal interaction to solve a clear problem.

Product-line keywords:

- Pixel style.
- Android phone and tablet.
- Small utility tools.
- Fast launch.
- Single core task.
- Unified visual language.
- `CODEX & XUE` co-development identity.

## 2. Product Boundaries

Projects that fit this product line:

- Calculation, conversion, recording, measurement, generation, and assisted-decision tools.
- Tools that can complete one task independently on a mobile device.
- MVPs that can be explained with one main screen or one main flow.
- Products that can create value without a complex account system.

Projects that are not default directions:

- Content communities.
- Heavy social products.
- Large SaaS clients.
- Products that require a complex backend before value can be validated.
- Products that mainly depend on marketing pages rather than tool experience.

## 3. Unified Naming

### 3.1 Product Name

Default naming format:

```text
Pixel<Name>
```

Examples:

- `PixelCalc`
- `PixelTimer`
- `PixelConvert`
- `PixelNote`

Naming requirements:

- Short.
- Readable.
- Expresses the tool's purpose.
- Avoids overly abstract or emotional names.
- Avoids names highly similar to existing major brands.

### 3.2 Android App Name

The Android launcher display name should match the product name by default. If the product name is too long, shorten the product name instead of inventing a separate launcher nickname.

### 3.3 Package Name

Default package format:

```text
com.milesxue.<product>
```

`<product>` uses lowercase English letters, with no spaces, hyphens, or underscores.

Examples:

```text
com.milesxue.pixelcalc
com.milesxue.pixeltimer
com.milesxue.pixelconvert
```

Early legacy projects that already use other package names are not forced to migrate immediately. New projects must follow this rule.

## 4. Developer Identity

Every product must show developer identity in at least one place.

Use this in UI, documents, README files, metadata, copyright, release notes, or explanatory text:

```text
CODEX & XUE
```

Allowed locations:

- About page.
- Settings page footer.
- Main-screen status bar.
- Splash screen.
- README.
- App details or release notes.

Display rules:

- The identity must be restrained and must not overpower the tool.
- It must not appear as a giant logo occupying the first screen.
- It must not interfere with core tool operations.
- It should keep a monospace or system-font style consistent with the product name.

## 5. Cross-Product Consistency

All products must share these consistencies:

- The same Claude/Anthropic color-token system, defined in `DESIGN_SPEC.md`.
- The same pixel-component language.
- The same naming structure.
- The same README status discipline, defined in `PROJECT_SPEC.md`.
- The same MVP-first engineering workflow, defined in `PROJECT_SPEC.md`.
- The same developer identity.

Consistency does not mean every product must look identical. Each tool may adjust layout for its task type, but users should immediately recognize that the products belong to the same line.

## 6. Phone And Tablet Strategy

All products support phones and tablets by default.

Product-level platform intent:

- Phone use must feel immediate, touch-friendly, and capable of completing the core task without landscape dependence.
- Tablet use must add useful working space when the task benefits from it, without turning the product into a larger or less focused tool.
- Detailed phone and tablet layout rules belong to `DESIGN_SPEC.md`.

## 7. Product Experience Tone

Products should feel:

- Ready immediately after launch.
- Quiet.
- Non-showy.
- Trustworthy in their results.
- Short in operation path.
- Intentional in every control.

Products should not feel:

- Feature-stacked.
- Overloaded with settings.
- Visually over-packaged.
- Dependent on learning complex rules first.
- Unclear on the first screen.

## 8. PixelCalc's Role

`PixelCalc` is an early MVP / legacy reference, not the product-line design authority.

It may be used to:

- Understand the shape of existing projects in this directory.
- Observe an early attempt at a pixel-style utility.
- Reference migration cost and bias correction.

It must not be used to:

- Prove that a design choice is necessarily correct.
- Override the Claude/Anthropic tokens in `DESIGN_SPEC.md`.
- Become a template that future projects must copy.

If `PixelCalc` conflicts with the current three specifications, the current three specifications win.

## 9. New Product Start Checklist

Before creating any new product, confirm:

- Product name follows `Pixel<Name>`.
- Package name follows `com.milesxue.<product>`.
- The MVP can be explained in one sentence.
- The first screen is the tool itself.
- At least one `CODEX & XUE` identity placement is planned.
- UI will follow `DESIGN_SPEC.md`.
- Engineering will follow `PROJECT_SPEC.md`.

## 10. Sources

- [Anthropic official site](https://www.anthropic.com/)
- [Claude Code product page](https://claude.com/product/claude-code)
- [Claude Design product page](https://claude.com/product/claude-design)
- [Claude download page](https://claude.com/download)
