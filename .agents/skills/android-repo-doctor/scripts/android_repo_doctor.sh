#!/usr/bin/env bash

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
ROOT="$DEFAULT_ROOT"
REPOS=(".")
FORMAT="text"

usage() {
    cat <<'USAGE'
Usage: android_repo_doctor.sh [--root PATH] [--repos REPO[,REPO...]] [--format text|json]

Options also accept PowerShell-style names: -Root, -Repos, and -Format.
USAGE
}

split_csv_into_repos() {
    local value="$1"
    local old_ifs="$IFS"
    IFS=','
    read -r -a REPOS <<< "$value"
    IFS="$old_ifs"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --root|-Root)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            ROOT="$2"
            shift 2
            ;;
        --root=*)
            ROOT="${1#*=}"
            shift
            ;;
        --repos|-Repos)
            [ "$#" -ge 2 ] || { echo "Missing value for $1" >&2; exit 2; }
            split_csv_into_repos "$2"
            shift 2
            ;;
        --repos=*)
            split_csv_into_repos "${1#*=}"
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

case "$FORMAT" in
    text|json) ;;
    *)
        echo "Invalid format: $FORMAT" >&2
        exit 2
        ;;
esac

if ! RESOLVED_ROOT="$(cd "$ROOT" 2>/dev/null && pwd)"; then
    echo "Repository root not found: $ROOT" >&2
    exit 1
fi

FAILURES=""
WARNINGS=""
INFO=""
REPO_NAMES=()
REPO_PATHS=()
REPO_STATUSES=()
REPO_FAILURES=()
REPO_WARNINGS=()
REPO_INFO=()

append_unique() {
    local var_name="$1"
    local value="$2"
    local current

    [ -n "$value" ] || return
    eval "current=\${$var_name-}"
    if [ -n "$current" ] && printf '%s\n' "$current" | grep -Fx -- "$value" >/dev/null 2>&1; then
        return
    fi

    if [ -z "$current" ]; then
        printf -v "$var_name" '%s' "$value"
    else
        printf -v "$var_name" '%s\n%s' "$current" "$value"
    fi
}

add_failure() {
    append_unique FAILURES "$1"
}

add_warning() {
    append_unique WARNINGS "$1"
}

add_info() {
    append_unique INFO "$1"
}

normalize_path() {
    local path="$1"
    printf '%s' "${path//\\//}"
}

trim() {
    printf '%s' "$1" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

test_any_path_exists() {
    local repo_path="$1"
    shift
    local candidate

    for candidate in "$@"; do
        if [ -e "$repo_path/$candidate" ]; then
            return 0
        fi
    done
    return 1
}

test_tracked_pollution() {
    local path
    path="$(normalize_path "$1")"

    if [[ "$path" =~ (^|/)(\.gradle|build|\.kotlin|captures|\.externalNativeBuild|\.cxx)(/|$) ]]; then
        printf '%s' "generated/cache path"
        return 0
    fi
    if [[ "$path" =~ (^|/)local\.properties$ ]]; then
        printf '%s' "local Android SDK properties"
        return 0
    fi
    if [[ "$path" =~ (^|/)\.idea/(workspace\.xml|usage\.statistics\.xml|shelf/|caches/|libraries/|modules\.xml|assetWizardSettings\.xml|navEditor\.xml) ]]; then
        printf '%s' "local Android Studio metadata"
        return 0
    fi
    if [[ "$path" =~ \.(apk|aab|ap_|idsig)$ ]]; then
        printf '%s' "built Android artifact"
        return 0
    fi
    if [[ "$path" =~ \.(jks|keystore|p12|pem)$ ]]; then
        printf '%s' "possible signing secret"
        return 0
    fi
    if [[ "$path" =~ (^|/)(key|signing|release|keystore)\.properties$ ]]; then
        printf '%s' "possible signing properties"
        return 0
    fi

    return 1
}

test_visible_status_pollution() {
    local status_line="$1"
    local path
    local reason

    if [ "${#status_line}" -lt 4 ]; then
        return 1
    fi

    path="$(trim "${status_line:3}")"
    path="${path#\"}"
    path="${path%\"}"

    if reason="$(test_tracked_pollution "$path")"; then
        printf '%s' "$path ($reason) is visible in normal git status; it should be ignored or removed from tracking."
        return 0
    fi

    return 1
}

gitignore_contains() {
    local file="$1"
    shift
    local line
    local trimmed
    local pattern

    while IFS= read -r line || [ -n "$line" ]; do
        trimmed="$(trim "$line")"
        case "$trimmed" in
            ""|\#*) continue ;;
        esac
        for pattern in "$@"; do
            if [ "$trimmed" = "$pattern" ]; then
                return 0
            fi
        done
    done < "$file"

    return 1
}

GIT_OUT_FILE=""
GIT_ERR_FILE=""
GIT_EXIT=0

cleanup_git_temp() {
    [ -n "$GIT_OUT_FILE" ] && rm -f "$GIT_OUT_FILE"
    [ -n "$GIT_ERR_FILE" ] && rm -f "$GIT_ERR_FILE"
    GIT_OUT_FILE=""
    GIT_ERR_FILE=""
}

run_git() {
    local cwd="$1"
    shift
    local safe_path

    cleanup_git_temp
    GIT_OUT_FILE="$(mktemp "${TMPDIR:-/tmp}/android-repo-doctor-out.XXXXXX")"
    GIT_ERR_FILE="$(mktemp "${TMPDIR:-/tmp}/android-repo-doctor-err.XXXXXX")"
    safe_path="$(normalize_path "$cwd")"
    git -c "safe.directory=$safe_path" -C "$cwd" "$@" >"$GIT_OUT_FILE" 2>"$GIT_ERR_FILE"
    GIT_EXIT=$?
}

process_git_stderr() {
    local command_label="$1"
    local candidate="${2:-}"
    local line

    while IFS= read -r line || [ -n "$line" ]; do
        [ -n "$line" ] || continue
        if [[ "$line" =~ ^warning: ]]; then
            add_warning "Git environment warning: $line"
        elif [[ "$line" =~ ^fatal: ]]; then
            if [ -n "$candidate" ]; then
                add_failure "$command_label failed for ${candidate}: $line"
            else
                add_failure "$command_label failed: $line"
            fi
        else
            if [ -n "$candidate" ]; then
                add_warning "$command_label stderr for ${candidate}: $line"
            else
                add_warning "$command_label stderr: $line"
            fi
        fi
    done < "$GIT_ERR_FILE"
}

record_repo_result() {
    local name="$1"
    local path="$2"
    local status="PASS"

    if [ -n "$FAILURES" ]; then
        status="FAIL"
    elif [ -n "$WARNINGS" ]; then
        status="WARN"
    fi

    REPO_NAMES[${#REPO_NAMES[@]}]="$name"
    REPO_PATHS[${#REPO_PATHS[@]}]="$path"
    REPO_STATUSES[${#REPO_STATUSES[@]}]="$status"
    REPO_FAILURES[${#REPO_FAILURES[@]}]="$FAILURES"
    REPO_WARNINGS[${#REPO_WARNINGS[@]}]="$WARNINGS"
    REPO_INFO[${#REPO_INFO[@]}]="$INFO"
}

test_repo() {
    local root_path="$1"
    local repo_name="$2"
    local repo_path
    local display_name
    local line
    local visible_pollution
    local reason
    local group
    local label
    local candidate_text
    local candidate
    local old_ifs
    local candidates
    local root_gitignore
    local app_gitignore

    FAILURES=""
    WARNINGS=""
    INFO=""

    if [ -z "$repo_name" ] || [ "$repo_name" = "." ]; then
        repo_path="$root_path"
    else
        repo_path="$root_path/$repo_name"
    fi

    if [ -z "$repo_name" ] || [ "$repo_name" = "." ]; then
        display_name="$(basename "$repo_path")"
    else
        display_name="$repo_name"
    fi

    if [ ! -e "$repo_path" ]; then
        add_failure "Repository directory is missing: $repo_path"
        record_repo_result "$display_name" "$repo_path"
        return
    fi

    if [ ! -d "$repo_path/.git" ]; then
        add_failure "Repository is not an independent Git repo."
        record_repo_result "$display_name" "$repo_path"
        return
    fi

    run_git "$repo_path" status --short
    process_git_stderr "git status"
    while IFS= read -r line || [ -n "$line" ]; do
        [ -n "$(trim "$line")" ] || continue
        add_info "Working tree change: $line"
        if visible_pollution="$(test_visible_status_pollution "$line")"; then
            add_failure "$visible_pollution"
        fi
    done < "$GIT_OUT_FILE"
    if [ "$GIT_EXIT" -ne 0 ]; then
        add_failure "git status failed with exit code $GIT_EXIT."
    fi
    cleanup_git_temp

    run_git "$repo_path" ls-files
    process_git_stderr "git ls-files"
    while IFS= read -r line || [ -n "$line" ]; do
        [ -n "$line" ] || continue
        if reason="$(test_tracked_pollution "$line")"; then
            add_failure "Tracked pollution: $line ($reason)."
        fi
    done < "$GIT_OUT_FILE"
    if [ "$GIT_EXIT" -ne 0 ]; then
        add_failure "git ls-files failed with exit code $GIT_EXIT."
    fi
    cleanup_git_temp

    for group in \
        "settings.gradle(.kts)|settings.gradle.kts,settings.gradle" \
        "root build.gradle(.kts)|build.gradle.kts,build.gradle" \
        "gradle.properties|gradle.properties" \
        "gradlew|gradlew" \
        "gradlew.bat|gradlew.bat" \
        "gradle-wrapper.jar|gradle/wrapper/gradle-wrapper.jar" \
        "gradle-wrapper.properties|gradle/wrapper/gradle-wrapper.properties" \
        "app build.gradle(.kts)|app/build.gradle.kts,app/build.gradle" \
        "main AndroidManifest.xml|app/src/main/AndroidManifest.xml" \
        "README.md|README.md"
    do
        label="${group%%|*}"
        candidate_text="${group#*|}"
        old_ifs="$IFS"
        IFS=','
        read -r -a candidates <<< "$candidate_text"
        IFS="$old_ifs"

        if ! test_any_path_exists "$repo_path" "${candidates[@]}"; then
            add_failure "Required project input is missing: $label."
            continue
        fi

        for candidate in "${candidates[@]}"; do
            [ -e "$repo_path/$candidate" ] || continue
            run_git "$repo_path" check-ignore -v -- "$candidate"
            process_git_stderr "git check-ignore" "$candidate"
            if [ "$GIT_EXIT" -eq 0 ]; then
                add_failure "Required project input is ignored by .gitignore: $candidate"
            fi
            cleanup_git_temp
        done
    done

    root_gitignore="$repo_path/.gitignore"
    if [ ! -e "$root_gitignore" ]; then
        add_warning "Root .gitignore is missing."
    else
        if gitignore_contains "$root_gitignore" "/.idea/" ".idea/" "/.idea" ".idea"; then
            add_warning "Root .gitignore ignores the entire .idea directory; prefer excluding only local Android Studio state."
        fi
        if ! gitignore_contains "$root_gitignore" "*.iml"; then
            add_warning "Root .gitignore should ignore generated IntelliJ module files (*.iml)."
        fi
        if ! gitignore_contains "$root_gitignore" ".gradle" ".gradle/"; then
            add_warning "Root .gitignore should ignore Gradle project cache (.gradle/)."
        fi
        if ! gitignore_contains "$root_gitignore" "/local.properties" "local.properties"; then
            add_warning "Root .gitignore should ignore local.properties."
        fi
        if ! gitignore_contains "$root_gitignore" "/build" "/build/" "build/" "build"; then
            add_warning "Root .gitignore should ignore root build output (/build/)."
        fi
        if ! gitignore_contains "$root_gitignore" "/captures" "/captures/"; then
            add_warning "Root .gitignore should ignore Android Studio captures (/captures/)."
        fi
        if ! gitignore_contains "$root_gitignore" ".externalNativeBuild" ".externalNativeBuild/"; then
            add_warning "Root .gitignore should ignore .externalNativeBuild/."
        fi
        if ! gitignore_contains "$root_gitignore" ".cxx" ".cxx/"; then
            add_warning "Root .gitignore should ignore .cxx/."
        fi
        if ! gitignore_contains "$root_gitignore" "/.idea/workspace.xml" ".idea/workspace.xml"; then
            add_warning "Root .gitignore should ignore .idea/workspace.xml."
        fi
    fi

    app_gitignore="$repo_path/app/.gitignore"
    if [ ! -e "$app_gitignore" ]; then
        add_warning "app/.gitignore is missing; module build output should be ignored with /build/."
    else
        if ! gitignore_contains "$app_gitignore" "/build" "/build/" "build/" "build"; then
            add_warning "app/.gitignore should ignore module build output (/build/)."
        fi
    fi

    record_repo_result "$display_name" "$repo_path"
}

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

json_array_from_lines() {
    local lines="$1"
    local first=1
    local line

    printf '['
    if [ -n "$lines" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            if [ "$first" -eq 0 ]; then
                printf ','
            fi
            json_string "$line"
            first=0
        done <<< "$lines"
    fi
    printf ']'
}

for repo in "${REPOS[@]}"; do
    test_repo "$RESOLVED_ROOT" "$repo"
done

OVERALL_STATUS="PASS"
for status in "${REPO_STATUSES[@]}"; do
    if [ "$status" = "FAIL" ]; then
        OVERALL_STATUS="FAIL"
        break
    elif [ "$status" = "WARN" ] && [ "$OVERALL_STATUS" != "FAIL" ]; then
        OVERALL_STATUS="WARN"
    fi
done

if [ "$FORMAT" = "json" ]; then
    printf '{'
    printf '"generatedAt":'
    json_string "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    printf ',"root":'
    json_string "$RESOLVED_ROOT"
    printf ',"overallStatus":'
    json_string "$OVERALL_STATUS"
    printf ',"repos":['
    for i in "${!REPO_NAMES[@]}"; do
        if [ "$i" -gt 0 ]; then
            printf ','
        fi
        printf '{'
        printf '"name":'
        json_string "${REPO_NAMES[$i]}"
        printf ',"path":'
        json_string "${REPO_PATHS[$i]}"
        printf ',"status":'
        json_string "${REPO_STATUSES[$i]}"
        printf ',"failures":'
        json_array_from_lines "${REPO_FAILURES[$i]}"
        printf ',"warnings":'
        json_array_from_lines "${REPO_WARNINGS[$i]}"
        printf ',"info":'
        json_array_from_lines "${REPO_INFO[$i]}"
        printf '}'
    done
    printf ']}\n'
else
    echo "Android Repo Doctor"
    echo "Root: $RESOLVED_ROOT"
    echo "Overall: $OVERALL_STATUS"
    echo ""
    for i in "${!REPO_NAMES[@]}"; do
        echo "${REPO_NAMES[$i]}: ${REPO_STATUSES[$i]}"
        if [ -n "${REPO_FAILURES[$i]}" ]; then
            while IFS= read -r line || [ -n "$line" ]; do
                echo "  FAIL: $line"
            done <<< "${REPO_FAILURES[$i]}"
        fi
        if [ -n "${REPO_WARNINGS[$i]}" ]; then
            while IFS= read -r line || [ -n "$line" ]; do
                echo "  WARN: $line"
            done <<< "${REPO_WARNINGS[$i]}"
        fi
        if [ -n "${REPO_INFO[$i]}" ]; then
            while IFS= read -r line || [ -n "$line" ]; do
                echo "  INFO: $line"
            done <<< "${REPO_INFO[$i]}"
        fi
        echo ""
    done
fi

if [ "$OVERALL_STATUS" = "FAIL" ]; then
    exit 1
fi

exit 0
