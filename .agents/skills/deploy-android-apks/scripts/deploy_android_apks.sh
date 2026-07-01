#!/usr/bin/env bash

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
WORKSPACE_ROOT="$DEFAULT_WORKSPACE_ROOT"
PROJECTS=(".")
DEVICE_NAME=""
TARGET_FOLDER="Download"
BUILD_MODE="MissingOrStale"
DELIVERY_MODE="Auto"
DRY_RUN=false
FORMAT="text"

usage() {
    cat <<'USAGE'
Usage: deploy_android_apks.sh [options]

Options:
  --workspace-root PATH       PixelDone repository root. Default: script-derived root.
  --projects NAME[,NAME...]   Direct Android project directories. Default: .
  --device-name TEXT          Select an adb device whose line contains this text.
  --target-folder NAME        Android shared-storage folder for adb push. Default: Download.
  --build-mode MODE           MissingOrStale, Never, or Always.
  --delivery-mode MODE        Auto, Install, or Copy.
  --dry-run                   Report the planned action without building, installing, or copying.
  --format FORMAT             text or json. Default: text.

PowerShell-style option names such as -BuildMode, -DeliveryMode, and -DryRun are also accepted.
USAGE
}

split_csv_into_projects() {
    local value="$1"
    local old_ifs="$IFS"
    IFS=','
    read -r -a PROJECTS <<< "$value"
    IFS="$old_ifs"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --workspace-root|-WorkspaceRoot)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            WORKSPACE_ROOT="$2"
            shift 2
            ;;
        --workspace-root=*)
            WORKSPACE_ROOT="${1#*=}"
            shift
            ;;
        --projects|-Projects)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            split_csv_into_projects "$2"
            shift 2
            ;;
        --projects=*)
            split_csv_into_projects "${1#*=}"
            shift
            ;;
        --device-name|-DeviceName)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            DEVICE_NAME="$2"
            shift 2
            ;;
        --device-name=*)
            DEVICE_NAME="${1#*=}"
            shift
            ;;
        --target-folder|-TargetFolder)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            TARGET_FOLDER="$2"
            shift 2
            ;;
        --target-folder=*)
            TARGET_FOLDER="${1#*=}"
            shift
            ;;
        --build-mode|-BuildMode)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            BUILD_MODE="$2"
            shift 2
            ;;
        --build-mode=*)
            BUILD_MODE="${1#*=}"
            shift
            ;;
        --delivery-mode|-DeliveryMode)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            DELIVERY_MODE="$2"
            shift 2
            ;;
        --delivery-mode=*)
            DELIVERY_MODE="${1#*=}"
            shift
            ;;
        --format|-Format)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            FORMAT="$2"
            shift 2
            ;;
        --format=*)
            FORMAT="${1#*=}"
            shift
            ;;
        --dry-run|-DryRun)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

case "$BUILD_MODE" in
    MissingOrStale|Never|Always) ;;
    *)
        echo "Invalid build mode: $BUILD_MODE" >&2
        exit 2
        ;;
esac

case "$DELIVERY_MODE" in
    Auto|Install|Copy) ;;
    *)
        echo "Invalid delivery mode: $DELIVERY_MODE" >&2
        exit 2
        ;;
esac

case "$FORMAT" in
    text|json) ;;
    *)
        echo "Invalid format: $FORMAT" >&2
        exit 2
        ;;
esac

if ! RESOLVED_WORKSPACE_ROOT="$(cd "$WORKSPACE_ROOT" 2>/dev/null && pwd)"; then
    echo "Repository root not found: $WORKSPACE_ROOT" >&2
    exit 1
fi

RESULT_PROJECTS=()
RESULT_SOURCE_APKS=()
RESULT_LOCAL_BYTES=()
RESULT_DEVICES=()
RESULT_TARGET_FOLDERS=()
RESULT_METHODS=()
RESULT_STATUSES=()
RESULT_VERIFIED=()

add_result() {
    RESULT_PROJECTS[${#RESULT_PROJECTS[@]}]="$1"
    RESULT_SOURCE_APKS[${#RESULT_SOURCE_APKS[@]}]="$2"
    RESULT_LOCAL_BYTES[${#RESULT_LOCAL_BYTES[@]}]="$3"
    RESULT_DEVICES[${#RESULT_DEVICES[@]}]="$4"
    RESULT_TARGET_FOLDERS[${#RESULT_TARGET_FOLDERS[@]}]="$5"
    RESULT_METHODS[${#RESULT_METHODS[@]}]="$6"
    RESULT_STATUSES[${#RESULT_STATUSES[@]}]="$7"
    RESULT_VERIFIED[${#RESULT_VERIFIED[@]}]="$8"
}

is_android_project() {
    local dir="$1"

    { [ -e "$dir/settings.gradle.kts" ] || [ -e "$dir/settings.gradle" ]; } &&
        { [ -e "$dir/app/build.gradle.kts" ] || [ -e "$dir/app/build.gradle" ]; }
}

PROJECT_DIRS=()
for project in "${PROJECTS[@]}"; do
    if [ -z "$project" ] || [ "$project" = "." ]; then
        candidate="$RESOLVED_WORKSPACE_ROOT"
    else
        candidate="$RESOLVED_WORKSPACE_ROOT/$project"
    fi
    if [ ! -d "$candidate" ]; then
        echo "Project directory not found: $candidate" >&2
        exit 1
    fi
    if ! is_android_project "$candidate"; then
        echo "Directory is not a direct Android app project: $candidate" >&2
        exit 1
    fi
    PROJECT_DIRS[${#PROJECT_DIRS[@]}]="$(cd "$candidate" && pwd)"
done

if [ "${#PROJECT_DIRS[@]}" -eq 0 ]; then
    echo "No direct Android app projects found under $RESOLVED_WORKSPACE_ROOT" >&2
    exit 1
fi

get_version_name() {
    local project_path="$1"
    local file
    local value

    for file in "$project_path/app/build.gradle.kts" "$project_path/app/build.gradle"; do
        [ -e "$file" ] || continue
        value="$(sed -nE "s/.*versionName[[:space:]]*(=|[[:space:]])[[:space:]]*[\"']([^\"']+)[\"'].*/\2/p" "$file" | head -n 1)"
        if [ -n "$value" ]; then
            printf '%s' "$value"
            return 0
        fi
    done

    return 1
}

file_mtime() {
    if stat -f %m "$1" >/dev/null 2>&1; then
        stat -f %m "$1"
    else
        stat -c %Y "$1"
    fi
}

file_size() {
    wc -c < "$1" | tr -d '[:space:]'
}

STATE_PROJECT_NAME=""
STATE_PROJECT_PATH=""
STATE_DEBUG_DIR=""
STATE_VERSION_NAME=""
STATE_EXPECTED_APK=""
STATE_APK=""
STATE_APP_DEBUG=""
STATE_IS_MISSING=true
STATE_IS_STALE=false

get_project_apk_state() {
    local project_dir="$1"
    local candidate
    local newest_mtime=0
    local candidate_mtime

    STATE_PROJECT_NAME="$(basename "$project_dir")"
    STATE_PROJECT_PATH="$project_dir"
    STATE_DEBUG_DIR="$project_dir/app/build/outputs/apk/debug"
    STATE_VERSION_NAME="$(get_version_name "$project_dir" || true)"
    STATE_EXPECTED_APK=""
    STATE_APK=""
    STATE_APP_DEBUG=""
    STATE_IS_MISSING=true
    STATE_IS_STALE=false

    if [ -n "$STATE_VERSION_NAME" ]; then
        STATE_EXPECTED_APK="$STATE_DEBUG_DIR/$STATE_PROJECT_NAME-$STATE_VERSION_NAME-debug.apk"
    fi

    if [ -n "$STATE_EXPECTED_APK" ] && [ -f "$STATE_EXPECTED_APK" ]; then
        STATE_APK="$STATE_EXPECTED_APK"
    fi

    if [ -z "$STATE_APK" ] && [ -z "$STATE_VERSION_NAME" ] && [ -d "$STATE_DEBUG_DIR" ]; then
        for candidate in "$STATE_DEBUG_DIR"/"$STATE_PROJECT_NAME"-*-debug.apk; do
            [ -e "$candidate" ] || continue
            case "$(basename "$candidate")" in
                *androidTest*) continue ;;
            esac
            candidate_mtime="$(file_mtime "$candidate")"
            if [ "$candidate_mtime" -gt "$newest_mtime" ]; then
                newest_mtime="$candidate_mtime"
                STATE_APK="$candidate"
            fi
        done
    fi

    if [ -f "$STATE_DEBUG_DIR/app-debug.apk" ]; then
        STATE_APP_DEBUG="$STATE_DEBUG_DIR/app-debug.apk"
    fi

    if [ -n "$STATE_APK" ]; then
        STATE_IS_MISSING=false
    fi

    if [ -n "$STATE_APK" ] && [ -n "$STATE_APP_DEBUG" ]; then
        if [ "$(file_mtime "$STATE_APK")" -lt "$(file_mtime "$STATE_APP_DEBUG")" ]; then
            STATE_IS_STALE=true
        fi
    fi
}

invoke_assemble_debug() {
    local project_path="$1"
    local gradlew="$project_path/gradlew"

    if [ ! -e "$gradlew" ]; then
        echo "Gradle wrapper not found: $gradlew" >&2
        return 1
    fi

    (cd "$project_path" && ./gradlew assembleDebug)
}

ADB_SERIAL=""
ADB_LINE=""

get_adb_target() {
    local requested="$1"
    local output
    local line
    local serial
    local detail
    local serials=()
    local lines=()
    local i

    ADB_SERIAL=""
    ADB_LINE=""

    command -v adb >/dev/null 2>&1 || return 1
    output="$(adb devices -l 2>/dev/null)" || return 1

    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            ""|"List of devices attached"|daemon\ *|\*\ daemon\ *) continue ;;
        esac
        if [[ "$line" =~ ^[[:space:]]*([^[:space:]]+)[[:space:]]+device[[:space:]]*(.*)$ ]]; then
            serial="${BASH_REMATCH[1]}"
            detail="${BASH_REMATCH[2]}"
            if [ -n "$requested" ]; then
                case "$line" in
                    *"$requested"*)
                        ADB_SERIAL="$serial"
                        ADB_LINE="$line"
                        return 0
                        ;;
                esac
            else
                serials[${#serials[@]}]="$serial"
                lines[${#lines[@]}]="$line"
                : "$detail"
            fi
        fi
    done <<< "$output"

    if [ -z "$requested" ] && [ "${#serials[@]}" -eq 1 ]; then
        ADB_SERIAL="${serials[0]}"
        ADB_LINE="${lines[0]}"
        return 0
    fi

    for i in "${!serials[@]}"; do
        : "$i"
    done
    return 1
}

copy_with_adb() {
    local serial="$1"
    local apk="$2"
    local folder="$3"
    local remote_path="/sdcard/$folder/$(basename "$apk")"
    local remote_listing
    local local_bytes
    local remote_bytes

    adb -s "$serial" push "$apk" "$remote_path" >/dev/null || return 1
    remote_listing="$(adb -s "$serial" shell "ls -ln '$remote_path'" 2>/dev/null | head -n 1)"
    if [[ "$remote_listing" =~ ^[^[:space:]]+[[:space:]]+[0-9]+[[:space:]]+[^[:space:]]+[[:space:]]+[^[:space:]]+[[:space:]]+([0-9]+)[[:space:]]+ ]]; then
        remote_bytes="${BASH_REMATCH[1]}"
        local_bytes="$(file_size "$apk")"
        [ "$remote_bytes" = "$local_bytes" ]
        return $?
    fi
    return 1
}

install_with_adb() {
    local serial="$1"
    local apk="$2"

    adb -s "$serial" install -r -d "$apk" >/dev/null
}

if get_adb_target "$DEVICE_NAME"; then
    ADB_AVAILABLE=true
else
    ADB_AVAILABLE=false
fi

if [ "$ADB_AVAILABLE" = true ] && [ "$DELIVERY_MODE" != "Copy" ]; then
    METHOD="adb-install"
elif [ "$ADB_AVAILABLE" = true ] && [ "$DELIVERY_MODE" = "Copy" ]; then
    METHOD="adb-push"
else
    METHOD="none"
fi

if [ "$ADB_AVAILABLE" = true ]; then
    DEVICE_LABEL="$ADB_SERIAL"
elif [ -n "$DEVICE_NAME" ]; then
    DEVICE_LABEL="$DEVICE_NAME"
else
    DEVICE_LABEL="unspecified"
fi

for project_dir in "${PROJECT_DIRS[@]}"; do
    get_project_apk_state "$project_dir"

    needs_build=false
    if [ "$BUILD_MODE" = "Always" ]; then
        needs_build=true
    elif [ "$BUILD_MODE" = "MissingOrStale" ] && { [ "$STATE_IS_MISSING" = true ] || [ "$STATE_IS_STALE" = true ]; }; then
        needs_build=true
    fi

    if [ "$needs_build" = true ]; then
        if [ "$DRY_RUN" = true ]; then
            source="$STATE_APK"
            [ -n "$source" ] || source="$STATE_EXPECTED_APK"
            bytes=""
            [ -n "$STATE_APK" ] && bytes="$(file_size "$STATE_APK")"
            add_result "$STATE_PROJECT_NAME" "$source" "$bytes" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "WouldBuild" "DryRun"
            continue
        fi

        if ! invoke_assemble_debug "$STATE_PROJECT_PATH"; then
            add_result "$STATE_PROJECT_NAME" "" "" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "BuildFailed" "false"
            continue
        fi

        get_project_apk_state "$project_dir"
        if [ -z "$STATE_APP_DEBUG" ]; then
            add_result "$STATE_PROJECT_NAME" "" "" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "MissingAppDebugAfterBuild" "false"
            continue
        fi
        if [ -z "$STATE_APK" ]; then
            missing_source="$STATE_EXPECTED_APK"
            add_result "$STATE_PROJECT_NAME" "$missing_source" "" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "MissingVersionedApkAfterBuild" "false"
            continue
        fi
    fi

    if [ "$BUILD_MODE" = "Never" ] && { [ "$STATE_IS_MISSING" = true ] || [ "$STATE_IS_STALE" = true ]; }; then
        status="MissingApk"
        [ "$STATE_IS_STALE" = true ] && status="StaleApk"
        source="$STATE_APK"
        [ -n "$source" ] || source="$STATE_EXPECTED_APK"
        bytes=""
        [ -n "$STATE_APK" ] && bytes="$(file_size "$STATE_APK")"
        add_result "$STATE_PROJECT_NAME" "$source" "$bytes" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "$status" "false"
        continue
    fi

    if [ -z "$STATE_APK" ]; then
        missing_source="$STATE_EXPECTED_APK"
        add_result "$STATE_PROJECT_NAME" "$missing_source" "" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "MissingApk" "false"
        continue
    fi

    if [ "$METHOD" = "none" ]; then
        add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "NoDevice" "false"
        continue
    fi

    if [ "$DRY_RUN" = true ]; then
        if [ "$METHOD" = "adb-install" ]; then
            add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "InstalledApp" "$METHOD" "WouldInstall" "DryRun"
        else
            add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "WouldDeploy" "DryRun"
        fi
        continue
    fi

    if [ "$METHOD" = "adb-install" ]; then
        install_output="$(adb -s "$ADB_SERIAL" install -r -d "$STATE_APK" 2>&1)"
        install_code=$?
        if [ "$install_code" -eq 0 ]; then
            add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "InstalledApp" "$METHOD" "Installed" "true"
        elif [ "$DELIVERY_MODE" = "Auto" ] && copy_with_adb "$ADB_SERIAL" "$STATE_APK" "$TARGET_FOLDER"; then
            add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "$TARGET_FOLDER" "adb-push" "CopiedAfterInstallFailed: $install_output" "true"
        else
            add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "Failed: $install_output" "false"
        fi
    elif [ "$METHOD" = "adb-push" ]; then
        if copy_with_adb "$ADB_SERIAL" "$STATE_APK" "$TARGET_FOLDER"; then
            add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "Copied" "true"
        else
            add_result "$STATE_PROJECT_NAME" "$STATE_APK" "$(file_size "$STATE_APK")" "$DEVICE_LABEL" "$TARGET_FOLDER" "$METHOD" "Failed: adb push or verification failed" "false"
        fi
    fi
done

json_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    value="${value//$'\n'/\\n}"
    value="${value//$'\r'/\\r}"
    value="${value//$'\t'/\\t}"
    printf '%s' "$value"
}

json_string() {
    printf '"%s"' "$(json_escape "$1")"
}

json_verified() {
    case "$1" in
        true|false) printf '%s' "$1" ;;
        *) json_string "$1" ;;
    esac
}

if [ "$FORMAT" = "json" ]; then
    printf '['
    for i in "${!RESULT_PROJECTS[@]}"; do
        if [ "$i" -gt 0 ]; then
            printf ','
        fi
        printf '{'
        printf '"Project":'
        json_string "${RESULT_PROJECTS[$i]}"
        printf ',"SourceApk":'
        json_string "${RESULT_SOURCE_APKS[$i]}"
        printf ',"LocalBytes":'
        if [ -n "${RESULT_LOCAL_BYTES[$i]}" ]; then
            printf '%s' "${RESULT_LOCAL_BYTES[$i]}"
        else
            printf 'null'
        fi
        printf ',"Device":'
        json_string "${RESULT_DEVICES[$i]}"
        printf ',"TargetFolder":'
        json_string "${RESULT_TARGET_FOLDERS[$i]}"
        printf ',"Method":'
        json_string "${RESULT_METHODS[$i]}"
        printf ',"Status":'
        json_string "${RESULT_STATUSES[$i]}"
        printf ',"Verified":'
        json_verified "${RESULT_VERIFIED[$i]}"
        printf '}'
    done
    printf ']\n'
else
    echo "Deploy Android APKs"
    for i in "${!RESULT_PROJECTS[@]}"; do
        echo "${RESULT_PROJECTS[$i]}:"
        echo "  SourceApk: ${RESULT_SOURCE_APKS[$i]}"
        if [ -n "${RESULT_LOCAL_BYTES[$i]}" ]; then
            echo "  LocalBytes: ${RESULT_LOCAL_BYTES[$i]}"
        else
            echo "  LocalBytes:"
        fi
        echo "  Device: ${RESULT_DEVICES[$i]}"
        echo "  TargetFolder: ${RESULT_TARGET_FOLDERS[$i]}"
        echo "  Method: ${RESULT_METHODS[$i]}"
        echo "  Status: ${RESULT_STATUSES[$i]}"
        echo "  Verified: ${RESULT_VERIFIED[$i]}"
        echo ""
    done
fi
