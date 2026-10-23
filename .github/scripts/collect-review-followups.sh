#!/usr/bin/env bash
#
# Turns the review findings a merged PR left behind into one tracked issue, so that
# "advisory" means queued rather than discarded.
#
# The blocking/advisory split only works if the advisory half survives the merge. The
# 30-day record says which mechanism survives and which does not, unambiguously:
#
#   survived — every one became a tracked artifact: tbc-doc-pipeline#98 -> issue #101 and
#              PR #104; #107's four open findings -> PR #110; fsmc#136 -> #137;
#              fivetran-dbt#202 -> #203; tbc-agents#283 -> #284
#   lost     — replied-to, or posted late, then died with the PR: tbc-platform#441 (five
#              real design-security findings, five minutes after merge, never addressed),
#              tbc-mcp-server#296 (cache poisoning, 64 seconds late), tbc-doc-pipeline#108
#              (a runtime banner contradicting the PR's own fix, never fixed)
#
# A finding survives if and only if it becomes an issue. A resolved thread is a deleted
# finding. So this does not ask the author to remember at merge time — which is exactly
# when the PR is green and they are done.
#
# What it collects: **every unresolved thread an automated reviewer (Copilot or Claude) raised
# on the merged PR**, whatever its tier and whenever it was posted. A thread a HUMAN started is
# deliberately not collected — test-collect-review-followups.sh pins that. Tier and lateness are annotations in the issue, never admission
# criteria — see the long note above the selection for the four separate times a predicate
# silently ate a finding. A thread posted after mergedAt had no chance to gate anything, and a
# thread tagged BLOCKING that is still open means the review gate was missed; both are called
# out at the top of the issue rather than filtered.
#
# Idempotent: one issue per PR, looked up by title and updated rather than duplicated.
# Safe to re-run, and safe for the daily sweep to revisit the same PR.
#
# Usage (env-driven; the workflow passes GitHub values through env:, never by
# interpolating them into the command line — see conventions/make-hub.md):
#   REPO=owner/name PR_NUMBER=123 ./collect-review-followups.sh
set -euo pipefail

: "${REPO:?REPO is required (owner/name)}"
: "${PR_NUMBER:?PR_NUMBER is required}"

command -v gh >/dev/null 2>&1 || {
	echo "✗ gh CLI not found" >&2
	exit 1
}
# shellcheck source-path=SCRIPTDIR source=issue-upsert.sh
source "$(dirname "${BASH_SOURCE[0]}")/issue-upsert.sh"

OWNER="${REPO%%/*}"
NAME="${REPO##*/}"
LABEL="review-followup"
TITLE="review follow-ups from #${PR_NUMBER}"

# Paginated, not first:100. The script promises to carry EVERY unresolved finding, and a
# fixed first page quietly breaks that promise on exactly the PRs that need it most — the
# long, heavily-reviewed ones. Threads beyond the page would be dropped with no signal,
# which is the same silent-degradation shape as the credential in workspace#153.
cursor=""
threads="[]"
while :; do
	# shellcheck disable=SC2016  # $owner/$name/$number/$after are GraphQL variables, bound below
	page=$(gh api graphql -f owner="$OWNER" -f name="$NAME" -F number="$PR_NUMBER" \
		-f after="$cursor" -f query='
	query($owner:String!,$name:String!,$number:Int!,$after:String){
	  repository(owner:$owner,name:$name){
	    pullRequest(number:$number){
	      mergedAt url
	      author{login}
	      reviewThreads(first:100, after:$after){
	        pageInfo{ hasNextPage endCursor }
	        nodes{
	          isResolved path line
	          comments(first:1){ nodes{ author{login} createdAt body url } }
	        }
	      }
	    }
	  }
	}' --jq '.data.repository.pullRequest')

	[[ -n "$page" && "$page" != "null" ]] || {
		echo "✗ could not read $REPO#$PR_NUMBER" >&2
		exit 1
	}

	threads=$(jq -s '.[0] + .[1]' <(printf '%s' "$threads") \
		<(jq '.reviewThreads.nodes' <<<"$page"))

	[[ "$(jq -r '.reviewThreads.pageInfo.hasNextPage' <<<"$page")" == "true" ]] || break
	cursor=$(jq -r '.reviewThreads.pageInfo.endCursor' <<<"$page")
done

# The scalar fields are identical on every page; keep the last page's and attach the full
# thread list so the jq below reads one object exactly as before.
pr=$(jq --argjson t "$threads" '{mergedAt, url, author} + {reviewThreads: {nodes: $t}}' <<<"$page")

merged_at=$(jq -r '.mergedAt // empty' <<<"$pr")
[[ -n "$merged_at" ]] || {
	echo "$REPO#$PR_NUMBER is not merged — nothing to collect."
	exit 0
}
pr_author=$(jq -r '.author.login // empty' <<<"$pr")

# Carry EVERY unresolved bot thread. Nothing about the decision to keep a finding is
# allowed to depend on parsing its text.
#
# That rule is written in blood. This script dropped findings on the floor three times, each
# time because some predicate had to match before the finding counted:
#   1. surfaced findings posted as issue comments, which this script does not read at all
#   2. a transient API failure demoting a thread back to that same unread comment
#   3. an author-login test that rejected the `github-actions[bot]` threads we post ourselves
#   4. `test("ADVISORY:")` missing a finding tagged `ADVISORY (dry-and-reuse.md):` — a
#      parenthetical between the word and the colon, and the finding vanished
#
# Every one was the same defect wearing a different hat: a finding reaching a state where
# nothing carries it. Tier and lateness are still computed, but only to ANNOTATE the issue —
# never to decide admission. A regex cannot lose a finding it does not gate.
#
# Admission is therefore: unresolved, and raised by an automated reviewer (the author test below:
# Copilot or Claude — a human's thread is out of scope). The marker clause covers the
# threads surface-suppressed-findings.sh posts under the workflow's GITHUB_TOKEN, which no
# author test would match.
findings=$(jq -r --arg merged "$merged_at" '
  [ .reviewThreads.nodes[]
    | select(.isResolved == false)
    | select((.comments.nodes | length) > 0)
    | . as $t
    | $t.comments.nodes[0] as $c
    | select(
        ($c.author.login | test("copilot|claude"; "i"))
        or ($c.body | test("<!-- surfaced-suppressed:"))
      )
    | {
        path: ($t.path // "—"),
        line: ($t.line // 0),
        url: $c.url,
        body: $c.body,
        late: ($c.createdAt > $merged),
        # Anchored to the START of a line, optionally behind markdown bold — the convention
        # is that the tag LEADS the finding. Searching the whole body instead reads the tier
        # out of incidental prose: "this is a non-blocking nit" or a finding that quotes the
        # gate would both render as BLOCKING and trip the 🚨 banner. `\b` alone does not save
        # you, because a hyphen is a word boundary too. The colon is not required, so a
        # qualifier — `ADVISORY (dry-and-reuse.md):` — still reads correctly.
        tier: (if ($c.body | test("(^|\n)\\s*(\\*\\*)?BLOCKING\\b")) then "BLOCKING"
               elif ($c.body | test("(^|\n)\\s*(\\*\\*)?ADVISORY\\b")) then "ADVISORY"
               else "untagged" end)
      }
  ]' <<<"$pr")

count=$(jq 'length' <<<"$findings")
if [[ "$count" == "0" ]]; then
	echo "$REPO#$PR_NUMBER: no unresolved reviewer threads."
	exit 0
fi

late_count=$(jq '[.[] | select(.late)] | length' <<<"$findings")
# Only PRE-merge blocking threads mean the gate was missed. A blocking thread posted
# after mergedAt never had a chance to gate anything — counting it here would fire the
# "should not have merged" banner and the "nothing could have gated them" banner at once,
# about the same thread.
blocking_count=$(jq '[.[] | select(.tier == "BLOCKING" and (.late | not))] | length' <<<"$findings")
late_blocking=$(jq '[.[] | select(.tier == "BLOCKING" and .late)] | length' <<<"$findings")

# The label may not exist in this repo yet. `--force` makes creation idempotent, and
# creating it here keeps the workflow self-contained rather than depending on a separate
# `make labels` pass having reached every repo first.
gh label create "$LABEL" --repo "$REPO" \
	--color "fbca04" \
	--description "Review threads left unresolved when a PR merged" \
	--force >/dev/null 2>&1 || true

# Built with explicit \n rather than heredocs: $(cat <<EOF) strips trailing newlines, so
# the sections would run together and the markdown would lose its paragraph breaks.
body="Reviewer threads left unresolved when $(jq -r '.url' <<<"$pr") merged."
body+=$'\n\n'
body+="An unresolved thread on a merged PR is a deleted finding, so every one is tracked here"
body+=$'\n'
body+="regardless of its tier — nothing decides what to keep by reading the text. Close this"
body+=$'\n'
body+="issue when they are handled or deliberately declined."
body+=$'\n\n'

if [[ "$blocking_count" != "0" ]]; then
	body+="🚨 **$blocking_count of these are tagged BLOCKING and should not have merged.**"
	body+=$'\n'
	body+="The review gate in \`conventions/commits-and-releases.md\` requires every blocking thread"
	body+=$'\n'
	body+="resolved first. Triage these before anything else, and work out how the gate was missed."
	body+=$'\n\n'
fi

if [[ "$late_count" != "0" ]]; then
	body+="⚠️ **$late_count were posted after the merge**, so nothing could have gated them."
	if [[ "$late_blocking" != "0" ]]; then
		body+=" **$late_blocking of those are tagged BLOCKING** — read them first."
	fi
	body+=$'\n'
	body+="Findings arriving late are how real defects have escaped here before."
	body+=$'\n\n'
fi

body+=$'---\n\n'
body+=$(jq -r '.[] | "### \(.tier) — `\(.path):\(.line)`\(if .late then "  ·  *posted after merge*" else "" end)\n\n\(.body)\n\n[thread](\(.url))\n"' <<<"$findings")

# The daily sweep revisits the same PRs, so the lookup must see an issue filed a minute ago —
# issue-upsert.sh explains why that rules out search and a capped list.
issue_find "$REPO" "$TITLE" "$LABEL"
issue_write "$REPO" "$ISSUE_NUMBER" "$TITLE" "$body" "$LABEL" "$pr_author"
if [[ "$ISSUE_CREATED" == true ]]; then
	echo "Filed $REPO#$ISSUE_NUMBER with $count thread(s) ($blocking_count blocking, $late_count post-merge)."
else
	echo "Updated $REPO#$ISSUE_NUMBER with $count thread(s) ($blocking_count blocking, $late_count post-merge)."
fi
