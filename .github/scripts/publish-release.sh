#!/usr/bin/env bash
# Publishes a GitHub release listing the commits deployed since the previous production commit.
# Commit subjects are grouped by their feat:, improve: or fix: prefix; the rest go under chore.
# Set DRY_RUN=1 to print the notes without creating a release.
set -euo pipefail

: "${PREVIOUS_SHA:?}"
: "${DEPLOY_SHA:?}"

if [[ "$PREVIOUS_SHA" == "$DEPLOY_SHA" ]]; then
    echo 'No new commits since the previous deploy.'
    exit 0
fi

subjects="$(git log --reverse --no-merges --format=%s "$PREVIOUS_SHA..$DEPLOY_SHA")"
notes="$(mktemp)"

append_section() {
    local title="$1"
    local lines="$2"
    if [[ -n "$lines" ]]; then
        printf '## %s\n%s\n\n' "$title" "$lines" >> "$notes"
    fi
}

append_section 'feat' "$(sed -n 's/^feat:[[:space:]]*/- /p' <<< "$subjects")"
append_section 'improve' "$(sed -n 's/^improve:[[:space:]]*/- /p' <<< "$subjects")"
append_section 'fix' "$(sed -n 's/^fix:[[:space:]]*/- /p' <<< "$subjects")"
append_section 'chore' "$(grep -Ev '^(feat|improve|fix):' <<< "$subjects" | sed 's/^/- /' || true)"
printf '배포 커밋: %s\n' "${DEPLOY_SHA:0:7}" >> "$notes"

deployed_at="$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M')"
tag="deploy-$(TZ=Asia/Seoul date '+%Y%m%d-%H%M')"

if [[ "${DRY_RUN:-}" == 1 ]]; then
    printf '# %s 배포 (%s)\n\n' "$deployed_at" "$tag"
    cat "$notes"
    exit 0
fi

gh release create "$tag" --target "$DEPLOY_SHA" --title "$deployed_at 배포" --notes-file "$notes"
