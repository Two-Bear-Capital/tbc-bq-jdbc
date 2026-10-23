#!/usr/bin/env bash
# shellcheck disable=SC2034  # the ISSUE_* results are read by the sourcing caller
#
# The one home for "keep exactly one open issue per title": find it, then edit it or file it.
# Sourced, not run. It was hand-written twice before this, a third copy was on its way
# (workspace#202), and the copies had already drifted on how a failed lookup reports itself.
#
# Results come back in variables, never on stdout. A caller would capture stdout with $(...),
# and bash does not carry `set -e` into a command substitution — so a failed `gh` inside the
# helper would carry on, report "no issue exists", and file a duplicate. Called as a plain
# statement, the helper runs in the caller's shell under the caller's `set -euo pipefail`.
#
#   issue_find  <repo> <title> [label]
#       ISSUE_JSON   the oldest open issue {number,title,body} with exactly <title>, or empty
#       ISSUE_NUMBER its number, or empty
#   issue_write <repo> <number|""> <title> <body> [label] [assignee]
#       edits <number> when given, otherwise files the issue (with <label>) and then tries
#       to assign <assignee>. Overwrites ISSUE_JSON.
#       ISSUE_NUMBER the issue written
#       ISSUE_CREATED true when it was filed now (and survived), false otherwise

# Exact title over the FULL open list, paginated. Not `--search`: the search index is
# eventually consistent, so an issue filed a minute ago can be missing from it and the next
# run would file it again. Not `gh issue list --limit N` either: a cap means the issue
# falls off the list once N newer ones exist, with the same result.
#
# No stderr redirect and no `|| true`: a failed lookup has to abort the run, because an
# empty result here means "no issue exists" and the next step files a duplicate.
#
# The OLDEST match, not the first: the API lists newest first, and issue_write's race
# settlement needs every run to pick the same survivor.
issue_find() {
	local repo="$1" title="$2" label="${3:-}" query="state=open&per_page=100"
	[[ -n "$label" ]] && query+="&labels=$(jq -rn --arg l "$label" '$l | @uri')"
	ISSUE_JSON=$(gh api --paginate --slurp "repos/$repo/issues?$query" |
		jq -c --arg t "$title" '[.[][] | select(.pull_request == null and .title == $t)
			| {number, title, body: (.body // "")}] | min_by(.number) // empty')
	ISSUE_NUMBER=$(jq -r '.number // empty' <<<"${ISSUE_JSON:-null}")
}

issue_write() {
	local repo="$1" number="$2" title="$3" body="$4" label="${5:-}" assignee="${6:-}"
	if [[ -n "$number" ]]; then
		gh issue edit "$number" --repo "$repo" --body "$body" >/dev/null
		ISSUE_NUMBER="$number"
		ISSUE_CREATED=false
		return
	fi

	local args=(--repo "$repo" --title "$title" --body "$body")
	[[ -n "$label" ]] && args+=(--label "$label")
	ISSUE_NUMBER=$(gh issue create "${args[@]}" | sed -E 's#.*/issues/##')
	ISSUE_CREATED=true

	# Find-then-create is a race: two runs can both miss the issue and both file it
	# (review-followups.yml: a PR's close-event run and the daily sweep sit in different
	# concurrency groups and can handle the same PR at once). Serialising the workflow would
	# not do — by default GitHub keeps one pending run per group and cancels the rest,
	# silently dropping a collection. So settle it after the fact: every racer looks again
	# and keeps the oldest.
	# The loser closes its own copy as a DUPLICATE — never "not planned", which the sweep
	# reads as a declined finding and would then suppress — and leaves the survivor alone.
	# Its body is the other run's snapshot, which may be the NEWER one; overwriting it could
	# drop a thread that run saw. Whatever either snapshot missed, the next run rewrites.
	local mine="$ISSUE_NUMBER"
	issue_find "$repo" "$title" "$label"
	if [[ -n "$ISSUE_NUMBER" && "$ISSUE_NUMBER" != "$mine" ]]; then
		gh issue close "$mine" --repo "$repo" --reason duplicate \
			--comment "Duplicate of #$ISSUE_NUMBER — filed by a concurrent run." >/dev/null
		ISSUE_CREATED=false
		return
	fi
	ISSUE_NUMBER="$mine"

	# Create first, then assign: GitHub rejects some assignees (an author outside the org, a
	# bot), and that must not cost us the issue, which is the whole point.
	[[ -n "$assignee" && "$assignee" != *"[bot]" ]] || return 0
	gh issue edit "$ISSUE_NUMBER" --repo "$repo" --add-assignee "$assignee" >/dev/null 2>&1 ||
		echo "  (could not assign $assignee — left unassigned)" >&2
}
