#!/usr/bin/env bash
#
# Backfill CHANGELOG.md with all historical releases using git-cliff
#
# This script generates a complete changelog from all git tags and commits.
# It should only be run once to create the initial historical changelog.
#
# Usage:
#   ./scripts/backfill-changelog.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CHANGELOG_FILE="$PROJECT_ROOT/CHANGELOG.md"
CHANGELOG_BACKUP="$PROJECT_ROOT/CHANGELOG.md.backup"

echo "==> Backfilling CHANGELOG.md for tbc-bq-jdbc"
echo ""

# Check if git-cliff is installed
if ! command -v git-cliff &> /dev/null; then
    echo "Error: git-cliff is not installed"
    echo ""
    echo "Install it with:"
    echo "  # macOS (Homebrew)"
    echo "  brew install git-cliff"
    echo ""
    echo "  # Linux (cargo)"
    echo "  cargo install git-cliff"
    echo ""
    echo "  # Or download from: https://github.com/orhun/git-cliff/releases"
    exit 1
fi

echo "git-cliff version: $(git-cliff --version)"
echo ""

# Create backup of existing CHANGELOG.md
if [ -f "$CHANGELOG_FILE" ]; then
    echo "==> Creating backup: $CHANGELOG_BACKUP"
    cp "$CHANGELOG_FILE" "$CHANGELOG_BACKUP"
fi

# Change to project root
cd "$PROJECT_ROOT"

# Count tags
TAG_COUNT=$(git tag | grep -c '^v' || true)
echo "==> Found $TAG_COUNT version tags"
echo ""

# Generate complete changelog from all tags
echo "==> Generating changelog for all releases..."
git-cliff --config cliff.toml --output "$CHANGELOG_FILE"

echo ""
echo "==> ✅ CHANGELOG.md has been generated!"
echo ""
echo "Next steps:"
echo "  1. Review the generated changelog: less CHANGELOG.md"
echo "  2. If it looks good, commit it:"
echo "     git add CHANGELOG.md cliff.toml scripts/backfill-changelog.sh"
echo "     git commit -m 'chore: add automated changelog generation with git-cliff'"
echo "  3. If you need to regenerate, restore backup:"
echo "     mv CHANGELOG.md.backup CHANGELOG.md"
echo ""