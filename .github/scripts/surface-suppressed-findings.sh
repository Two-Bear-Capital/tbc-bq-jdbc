#!/usr/bin/env bash
#
# Re-posts the findings Copilot collapsed into a "Suppressed comments" block as real review
# threads, so the author sees them in round one and the merge-time collector can carry any
# that go unaddressed.
#
# Why this exists: over 30 days, 26% of PRs across this org had at least one suppressed
# Copilot finding. They are not nitpicks — one was a `dry_run` flag that reached only one of
# four mutating calls, so a run advertised as making no writes uploaded files, queued parses
# and wrote audit notes. Being collapsed, it was invisible; the author's round-one fix missed
# it and the other reviewer re-found it a round later. Every suppressed finding is either a
# defect nobody acts on or an extra round.
#
# They are posted as REVIEW THREADS, not as a plain issue comment, and that is load-bearing
# in two ways: a thread can be resolved, and collect-review-followups.sh reads review threads
# — an issue comment would be invisible to it, so an unaddressed suppressed finding would be
# lost at merge, which is the exact failure this pair exists to prevent.
#
# The marker below is also the collector's admission key, because these threads are authored
# by github-actions[bot] rather than by a reviewer. That coupling is documented where the
# filter lives, in collect-review-followups.sh — don't restate it here, change it there.
#
# Each is tagged ADVISORY: because Copilot's own confidence ranking is what suppressed it.
# That tier is a floor, not a ceiling — the comment says to escalate a correctness or
# security item, and conventions/commits-and-releases.md allows raising a tier, never
# lowering one.
#
# Threads are anchored at FILE level (subject_type=file) rather than to a line. Copilot names
# a line, but it is a line in the file, not necessarily one in the diff hunk, and the API
# rejects a line-anchored comment outside the diff. The line number is kept in the body.
#
# Idempotent per FINDING: each comment carries an HTML marker of review id + item index, so
# re-running (a re-review, a replayed workflow, a retry after a failure) never double-posts
# and always retries exactly the items that did not land. A review-level marker would be
# written as soon as the first item posted, stranding any later item that failed.
#
# Usage (env-driven; the workflow passes GitHub values through env:, never by interpolating
# them into the command line — see conventions/make-hub.md):
#   REPO=owner/name PR_NUMBER=123 REVIEW_ID=456 ./surface-suppressed-findings.sh
#
# REVIEW_ID is optional: without it, every Copilot review on the PR is considered.
set -euo pipefail

: "${REPO:?REPO is required (owner/name)}"
: "${PR_NUMBER:?PR_NUMBER is required}"
REVIEW_ID="${REVIEW_ID:-}"

for tool in gh jq python3; do
	command -v "$tool" >/dev/null 2>&1 || {
		echo "✗ $tool not found" >&2
		exit 1
	}
done

head_sha=$(gh api "repos/$REPO/pulls/$PR_NUMBER" --jq '.head.sha')
[[ -n "$head_sha" ]] || {
	echo "✗ could not read head SHA for $REPO#$PR_NUMBER" >&2
	exit 1
}

# Files actually in the diff. A comment on a path outside it is rejected, and Copilot's
# block occasionally cites a neighbouring file it reasoned about; those fall back to a
# PR-level comment rather than being dropped.
changed=$(gh api "repos/$REPO/pulls/$PR_NUMBER/files" --paginate --jq '.[].filename')

existing=$(gh api "repos/$REPO/pulls/$PR_NUMBER/comments" --paginate --jq '.[].body' 2>/dev/null || true)
existing_issue=$(gh api "repos/$REPO/issues/$PR_NUMBER/comments" --paginate --jq '.[].body' 2>/dev/null || true)

reviews=$(gh api "repos/$REPO/pulls/$PR_NUMBER/reviews" --paginate \
	--jq '.[] | select(.user.login | test("copilot"; "i")) | {id, body} | @json' 2>/dev/null || true)

[[ -n "$reviews" ]] || {
	echo "No Copilot reviews on $REPO#$PR_NUMBER — nothing to surface."
	exit 0
}

surfaced=0
failures=0
while IFS= read -r review; do
	[[ -n "$review" ]] || continue
	id=$(jq -r '.id' <<<"$review")
	[[ -z "$REVIEW_ID" || "$id" == "$REVIEW_ID" ]] || continue

	# Split the block into one item per finding. Copilot's shape is a `**path:line**` header
	# followed by `* body` bullets and optional fenced code, repeating until the end of the
	# review body. Parsed in Python rather than awk because the bodies contain backticks,
	# asterisks and fences that trip line-oriented matching.
	items=$(jq -r '.body // ""' <<<"$review" | python3 -c '
import json, re, sys

text = sys.stdin.read()
start = re.search(r"#+\s*Suppressed comments", text, re.I)
if not start:
    print("[]"); raise SystemExit

body = text[start.end():]
# A header is a bold path, optionally :line, alone on its line.
parts = re.split(r"^\*\*([^*\n]+?)\*\*\s*$", body, flags=re.M)
items = []
for i in range(1, len(parts) - 1, 2):
    anchor, chunk = parts[i].strip(), parts[i + 1].strip()
    m = re.match(r"^(.*?):(\d+)$", anchor)
    path, line = (m.group(1), m.group(2)) if m else (anchor, "")
    if chunk:
        items.append({"path": path, "line": line, "body": chunk})
print(json.dumps(items))
')

	n=$(jq 'length' <<<"$items")
	[[ "$n" != "0" ]] || continue

	idx=0
	while IFS= read -r item; do
		[[ -n "$item" ]] || continue
		idx=$((idx + 1))
		path=$(jq -r '.path' <<<"$item")
		line=$(jq -r '.line' <<<"$item")
		text=$(jq -r '.body' <<<"$item")

		# The marker is per ITEM, not per review. A review-level marker written after the
		# first item posted would make a retry skip the whole review — permanently
		# stranding any item that failed, with no way to promote it to a real thread.
		marker="<!-- surfaced-suppressed:${id}:${idx} -->"
		if grep -qF "$marker" <<<"$existing$existing_issue"; then
			echo "  already surfaced: $path (item $idx)"
			continue
		fi

		where=$([[ -n "$line" ]] && echo " (around line $line)" || echo "")
		comment="${marker}
**ADVISORY: surfaced from Copilot's suppressed findings**${where}

$text

---
*Copilot generated this and collapsed it rather than posting it. Suppressed findings here
have included real defects, so it is surfaced as a thread you can resolve. Tagged advisory
because Copilot's own confidence ranking hid it — **escalate it** if it is a correctness or
security issue. Left unresolved, it is filed automatically when this PR merges.*"

		# Branch on diff membership ALONE. Folding it into an && with the API call meant a
		# transient 5xx on a path that IS in the diff produced a message asserting the path
		# was not — false, and it silently demoted the finding to a PR-level comment that
		# the merge-time collector does not read. That is the same loss this script exists
		# to prevent, just triggered by a flaky API instead of an out-of-diff path.
		if ! grep -qxF "$path" <<<"$changed"; then
			gh api "repos/$REPO/issues/$PR_NUMBER/comments" -f body="$comment

⚠️ *\`$path\` is not in this PR's diff, so this could not be posted as a resolvable thread —
it will **not** be carried into the merge-time follow-up issue. Handle it here.*" >/dev/null
			echo "  surfaced $path${where} (PR-level — not in diff)"
			surfaced=$((surfaced + 1))
		elif gh api "repos/$REPO/pulls/$PR_NUMBER/comments" \
			-f commit_id="$head_sha" -f path="$path" -f subject_type=file \
			-f body="$comment" >/dev/null 2>&1; then
			echo "  surfaced $path${where}"
			surfaced=$((surfaced + 1))
		else
			# In the diff but the thread API refused it: a real error, not a routing
			# decision. Say so and keep going — the remaining items still deserve to be
			# surfaced — but exit non-zero so the run is visibly incomplete. A re-run
			# retries exactly this item, because the marker is per item and was never
			# written for it.
			echo "::warning::could not post a review thread for $path on $REPO#$PR_NUMBER — re-run to retry"
			echo "  ✗ FAILED $path${where} — left unsurfaced for retry"
			failures=$((failures + 1))
		fi
	done < <(jq -c '.[]' <<<"$items")

	echo "Copilot review $id: $n suppressed finding(s) considered."
done <<<"$reviews"

if ((surfaced == 0 && failures == 0)); then
	echo "No unsurfaced suppressed findings on $REPO#$PR_NUMBER."
fi

# Non-zero on any failed post, AFTER attempting every item. A silently-degraded run here
# means a finding nobody sees and the collector never carries — the failure mode this
# script exists to close, so it must be loud.
if ((failures > 0)); then
	echo "✗ $failures suppressed finding(s) could not be posted as threads on $REPO#$PR_NUMBER" >&2
	exit 1
fi
