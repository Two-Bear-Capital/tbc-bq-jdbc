#!/usr/bin/env bash
# Fail if a repo secret no longer matches the Terraform output it was set from.
# Called by .github/workflows/terraform.yml after apply, from terraform/.
#
# WHY VERIFY, NOT WRITE
# This step used to write WIF_PROVIDER and WIF_SERVICE_ACCOUNT with
# `gh secret set`, which needs a token with Secrets: write. GITHUB_TOKEN cannot
# be granted that, so it ran on an admin's personal access token, which failed
# with 403 once that token lost access, and the org does not allow PATs
# (workspace#326). None of the org's GitHub Apps holds Secrets: write either,
# and minting one for values that last changed in July 2026 would be a standing
# credential for a once-a-year edit. Reading a secret needs no token at all: the
# workflow passes each one in through env, so this compares and leaves the
# rare update to a human.
#
# Values are never printed: this repo is public and its logs are too.
set -euo pipefail

# "SECRET_NAME output_name" — the mapping docs/contributing/INTEGRATION_TESTS.md
# documents under "Required GitHub secrets".
PAIRS=(
	"WIF_PROVIDER wif_provider"
	"WIF_SERVICE_ACCOUNT ci_service_account_email"
	"BQ_TEST_PROJECT project_id"
	"BQ_TEST_DATASET dataset_id"
	"BQ_TEST_IMPERSONATE_SA impersonation_target_service_account"
	"BQ_TEST_IMPERSONATE_DELEGATE impersonation_delegate_service_account"
)

stale=()
for pair in "${PAIRS[@]}"; do
	read -r secret output <<<"$pair"
	want="$(terraform output -raw "$output")"
	if [[ "${!secret:-}" != "$want" ]]; then
		stale+=("$secret|$output")
	fi
done

if [[ ${#stale[@]} -eq 0 ]]; then
	echo "✓ all ${#PAIRS[@]} Terraform-derived secrets match their outputs"
	exit 0
fi

for entry in "${stale[@]}"; do
	secret="${entry%%|*}"
	output="${entry#*|}"
	echo "::error::$secret does not match terraform output $output. From terraform/, run: terraform output -raw $output | gh secret set $secret --repo $GITHUB_REPOSITORY"
done
exit 1
