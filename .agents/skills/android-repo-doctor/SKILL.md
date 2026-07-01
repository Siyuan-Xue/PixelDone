---
name: android-repo-doctor
description: Audit PixelPark Android subprojects for Git pollution, tracked generated files, local Android Studio/Gradle state, and .gitignore drift. Use when Codex needs to check Android Studio template repositories, validate repository hygiene before MVP work or release wrap-up, review whether local build artifacts are being tracked, or run the scheduled PixelPark repo pollution doctor.
---

# Android Repo Doctor

## Overview

Use this skill to run a read-only hygiene check on the standalone PixelDone Android repository. It separates real repository pollution from ordinary source-code work-in-progress, so dirty Kotlin files are reported as `INFO` while tracked generated files, local config, and required project files ignored by mistake are reported as failures.

## Quick Start

From the PixelDone repository root:

```powershell
.\.agents\skills\android-repo-doctor\scripts\android_repo_doctor.ps1
```

For machine-readable output:

```powershell
.\.agents\skills\android-repo-doctor\scripts\android_repo_doctor.ps1 -Format json
```

Default coverage is the current PixelDone repository. Override `-Root` only when the user explicitly asks.

## Workflow

1. Read `PROJECT_SPEC.md`, `PRODUCT_LINE_SPEC.md`, and `DESIGN_SPEC.md` before using this skill in the PixelDone repository.
2. Run the bundled script in read-only mode; do not edit `.gitignore` files or clean build outputs unless the user asks for remediation.
3. Report each repo as `PASS`, `WARN`, or `FAIL`.
4. Treat `FAIL` as action-needed repository pollution or broken hygiene.
5. Treat `WARN` as policy drift or environment warnings that should be reviewed.
6. Treat `INFO` as context, commonly ordinary dirty source files.

## Baseline Reference

Read `references/android-gitignore-baseline.md` when explaining why a file should be ignored or committed, or when proposing `.gitignore` remediation. Keep SKILL.md focused on workflow; keep source policy details in the reference.

## Output Contract

- Exit `0` when no repo has failures.
- Exit `1` when any repo has failures.
- JSON output includes `overallStatus`, `repos`, `failures`, `warnings`, and `info`.
- Text output is suitable for a concise user-facing report.

## Scheduled Use

For scheduled PixelDone hygiene automation, run the script against this repository and report results only. Do not write files, delete artifacts, stage changes, or repair `.gitignore` from the scheduled check.
