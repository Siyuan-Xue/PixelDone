# Android Gitignore Baseline

Use this reference when auditing Android Studio / Gradle projects created from the standard Empty Activity-style template.

## Official Basis

- Android build structure: `local.properties` is local machine configuration and must be excluded from source control; `.gradle/` is Gradle project cache; `.idea/` is Android Studio project metadata; wrapper files and build files are project inputs.
- Android build configuration: `settings.gradle(.kts)`, root `build.gradle(.kts)`, module `build.gradle(.kts)`, `gradle.properties`, and source sets are build inputs.
- Gradle wrapper: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties` must be checked into version control.
- Gradle-managed directories: project `.gradle/` and `build/` directories are generated/transient.
- JetBrains VCS guidance: share non-user-specific `.idea` metadata when useful, but exclude user-specific local state such as `workspace.xml`; for Gradle projects, generated `.iml` and some generated `.idea` files may be excluded.

## Ignore

Ignore generated or local-only state:

```gitignore
*.iml
.gradle/
/local.properties
/.idea/caches/
/.idea/libraries/
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build/
/captures/
.externalNativeBuild/
.cxx/
```

Each Android module should also ignore its own build output:

```gitignore
/build/
```

This normally lives in `app/.gitignore`.

## Do Not Ignore

Do not ignore or remove these project inputs:

- `settings.gradle` or `settings.gradle.kts`
- root `build.gradle` or `build.gradle.kts`
- module `build.gradle` or `build.gradle.kts`
- `gradle.properties`
- `gradlew` and `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/libs.versions.toml` when present
- `app/src/**`
- `AndroidManifest.xml`
- Android resources under `res/`
- unit and instrumentation tests under `test/` and `androidTest/`
- `proguard-rules.pro`
- `README.md`

## Policy Notes

- Warn on an overbroad root `/.idea/` ignore. It is conservative for local-only work, but it can hide shareable Android Studio project metadata. Prefer listing local `.idea` files explicitly.
- Fail if required build inputs are missing or ignored by mistake.
- Fail if generated files, local SDK paths, APK/AAB outputs, or signing secrets are already tracked.
- Report ordinary source-file modifications as informational context, not repository pollution.

## Source Links

- Android build structure: https://developer.android.com/build/android-build-structure
- Android build configuration: https://developer.android.com/build
- Gradle wrapper: https://docs.gradle.org/current/userguide/gradle_wrapper.html
- Gradle-managed directories: https://docs.gradle.org/current/userguide/directory_layout.html
- JetBrains VCS guidance: https://intellij-support.jetbrains.com/hc/en-us/articles/206544839-How-to-manage-projects-under-Version-Control-Systems
