# Automated Changelog Setup - Complete Guide

This document explains the automated changelog system now configured for tbc-bq-jdbc.

## 📋 What Was Set Up

### 1. git-cliff Configuration (`cliff.toml`)
- Professional changelog generator configuration
- Conventional Commits format support
- Automatic categorization: Features, Bug Fixes, Performance, Documentation, etc.
- Links to GitHub issues and pull requests
- Skips automated commits (version bumps, dependency updates)
- Emoji-enhanced sections for readability

### 2. Backfill Script (`scripts/backfill-changelog.sh`)
- One-time script to generate changelog for all 52 historical releases
- Creates backup before generation
- Preserves git history analysis

### 3. Enhanced GitHub Actions Workflow
Updated `.github/workflows/version-and-release.yml` to:
- Install git-cliff in CI environment
- Generate changelog entry for each new release
- Update CHANGELOG.md automatically
- Commit changelog before creating git tag
- Include changelog content in GitHub Release notes
- Add artifacts section to releases

### 4. Documentation
- `scripts/README.md` - Complete scripts documentation
- `CLAUDE.md` - Updated with changelog conventions
- This file - Setup guide and usage instructions

## 🚀 Quick Start

### Step 1: Install git-cliff Locally

```bash
# macOS (Homebrew)
brew install git-cliff

# Linux (Cargo - requires Rust)
cargo install git-cliff

# Or download binary from:
# https://github.com/orhun/git-cliff/releases
```

### Step 2: Backfill Historical Changelog

```bash
# Run the backfill script
./scripts/backfill-changelog.sh

# Review the generated CHANGELOG.md
less CHANGELOG.md

# Compare with backup if needed
diff CHANGELOG.md CHANGELOG.md.backup
```

### Step 3: Commit the Changes

```bash
# Add all changelog-related files
git add CHANGELOG.md cliff.toml scripts/ CLAUDE.md

# Commit with conventional format
git commit -m "chore: add automated changelog generation with git-cliff

- Configure git-cliff for conventional commits
- Add backfill script
- Update GitHub Actions workflow for automatic changelog updates
- Update CLAUDE.md with changelog conventions"

# Push to trigger CI
git push origin main
```

### Step 4: Verify Automation

After the next commit to main:
1. Build workflow will run
2. Release workflow will trigger
3. Changelog will be updated automatically
4. New GitHub Release will include changelog

## 📝 Daily Workflow

Every commit to main automatically triggers a release, so the changelog is always up-to-date with released versions.

### Writing Good Commit Messages

Use Conventional Commits format for automatic categorization:

```bash
# Features (shows in ⚡ Features section)
git commit -m "feat(auth): add support for workload identity federation"

# Bug Fixes (shows in 🐛 Bug Fixes section)
git commit -m "fix(metadata): correct precision for BIGNUMERIC columns"

# Performance (shows in 🚀 Performance section)
git commit -m "perf(cache): optimize parallel dataset loading with virtual threads"

# Documentation (shows in 📚 Documentation section)
git commit -m "docs(readme): add troubleshooting section for IntelliJ IDEA"

# Tests (shows in 🧪 Testing section)
git commit -m "test(integration): add coverage for PreparedStatement edge cases"

# Refactoring (shows in 🔨 Refactoring section)
git commit -m "refactor(connection): simplify session lifecycle management"

# Maintenance (shows in ⚙️ Miscellaneous Tasks section)
git commit -m "chore(deps): upgrade BigQuery SDK to 2.40.0"
```

### Manual Changelog Operations

```bash
# Regenerate entire changelog from all tags
git-cliff --config cliff.toml --output CHANGELOG.md

# Generate changelog for specific version range
git-cliff --config cliff.toml v1.0.45..v1.0.51
```

## 🔄 How Automation Works

### On Every Push to Main:

1. **Build Workflow** runs (build, tests, format checks)
2. **If Build Succeeds** → Release Workflow triggers:
   ```
   a. Bump version (e.g., 1.0.51 → 1.0.52)
   b. Commit version bump
   c. Update documentation versions
   d. Commit documentation updates
   e. Install git-cliff
   f. Generate changelog for new version ← NEW
   g. Commit CHANGELOG.md ← NEW
   h. Create git tag (e.g., v1.0.52)
   i. Push commits and tag
   j. Build artifacts
   k. Extract changelog for this release ← NEW
   l. Create GitHub Release with changelog ← ENHANCED
   m. Add artifacts section to release ← NEW
   n. Deploy to GitHub Packages
   ```

### Changelog Generation Process:

```bash
# In the workflow:
# 1. Create temporary tag for the new version
git tag -a "v${NEW_VERSION}" -m "Release version ${NEW_VERSION}"

# 2. Generate complete changelog (all releases including new one)
git-cliff --config cliff.toml --output CHANGELOG.md

# 3. Remove temporary tag (will be recreated later)
git tag -d "v${NEW_VERSION}"

# 4. Extract just this release's section for GitHub Release
git-cliff --config cliff.toml --latest --strip all > /tmp/release-notes.md

# 5. Commit updated CHANGELOG.md
git add CHANGELOG.md
git commit -m "chore: update CHANGELOG.md for version ${NEW_VERSION}"

# 6. Create real tag and GitHub Release
git tag -a "v${NEW_VERSION}" -m "Release version ${NEW_VERSION}"
gh release create "v${NEW_VERSION}" --notes-file /tmp/release-notes.md
```

## 📊 Changelog Structure

### Format

```markdown
# Changelog

All notable changes to this project will be documented in this file.

## [1.0.52] - 2026-02-10

### ⚡ Features
- **auth:** Add workload identity federation support
- **metadata:** Implement lazy loading for large projects

### 🐛 Bug Fixes
- **resultset:** Fix precision handling for NUMERIC types
- **connection:** Prevent session leak on connection close

### 🚀 Performance
- **cache:** Optimize parallel dataset loading with virtual threads
- **query:** Reduce BigQuery API calls with intelligent batching

### 📚 Documentation
- **readme:** Add IntelliJ IDEA troubleshooting guide
- **api:** Document all connection properties

## [1.0.51] - 2026-02-10
...
```

### Categories (in order)

1. ⚡ **Features** - New functionality
2. 🐛 **Bug Fixes** - Bug fixes
3. 🚀 **Performance** - Performance improvements
4. 📚 **Documentation** - Documentation changes
5. 🧪 **Testing** - Test additions/improvements
6. 🔨 **Refactoring** - Code refactoring
7. 🎨 **Styling** - Code style changes
8. ⚙️ **Miscellaneous Tasks** - Chores and maintenance
9. 👷 **CI/CD** - CI/CD pipeline changes
10. 🏗️ **Build** - Build system changes
11. ◀️ **Revert** - Reverted changes
12. 🔀 **Pull Requests** - Merged pull requests
13. 📦 **Other** - Uncategorized changes

## 🎯 Benefits

### For Maintainers
- ✅ **Zero Manual Work** - Changelog updates automatically on every release
- ✅ **Consistent Format** - Standardized changelog entries
- ✅ **Better Commits** - Encourages meaningful commit messages
- ✅ **Easy Review** - Check GitHub Release notes to verify changelog entries

### For Users
- ✅ **Clear Release Notes** - Each GitHub Release has detailed changelog
- ✅ **Categorized Changes** - Easy to find relevant changes
- ✅ **Linked Issues** - Direct links to GitHub issues and PRs
- ✅ **Historical Context** - Complete changelog from v1.0.0 onwards

### For Contributors
- ✅ **Clear Guidelines** - Conventional Commits format is well-documented
- ✅ **Immediate Feedback** - Check GitHub Release notes after merge to see how commits appear
- ✅ **Professional Output** - Contributions automatically documented

## 🔧 Customization

### Modify Changelog Categories

Edit `cliff.toml`:

```toml
commit_parsers = [
  { message = "^feat", group = "<!-- 0 -->⚡ Features" },
  { message = "^fix", group = "<!-- 1 -->🐛 Bug Fixes" },
  # Add custom category:
  { message = "^security", group = "<!-- 2 -->🔒 Security" },
  # ...
]
```

### Change Date Format

Edit `cliff.toml`:

```toml
body = """
## [{{ version | trim_start_matches(pat="v") }}] - {{ timestamp | date(format="%B %d, %Y") }}
"""
```

### Skip Certain Commits

Edit `cliff.toml`:

```toml
commit_parsers = [
  # Skip all commits with "WIP" in message
  { message = ".*WIP.*", skip = true },
  # ...
]
```

## 🐛 Troubleshooting

### Problem: Changelog not updating in CI

**Check:**
1. Verify git-cliff installation step succeeded
2. Check workflow logs for error messages
3. Ensure cliff.toml is in repository root

### Problem: Empty changelog sections

**Cause:** Commits don't match conventional format

**Solution:** Use conventional commits format. After release, check the GitHub Release notes to verify commits were recognized correctly.

### Problem: Want different commit message format

**Solution:** Edit `cliff.toml` commit_parsers section to match your format

### Problem: Backfill generates too much noise

**Solution:** Edit `cliff.toml` to skip more commit types before running backfill:

```toml
commit_parsers = [
  # Skip dependency updates
  { message = "^chore\\(deps\\)", skip = true },
  # Skip merge commits
  { message = "^Merge pull request", skip = true },
  # ...
]
```

## 📚 References

- [git-cliff Documentation](https://git-cliff.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Keep a Changelog](https://keepachangelog.com/)
- [Semantic Versioning](https://semver.org/)
- [scripts/README.md](scripts/README.md) - Scripts documentation
- [CLAUDE.md](CLAUDE.md) - Project development guidelines

## ✅ Checklist

- [ ] Install git-cliff locally (`brew install git-cliff`)
- [ ] Run backfill script (`./scripts/backfill-changelog.sh`)
- [ ] Review generated CHANGELOG.md (`less CHANGELOG.md`)
- [ ] Commit all changelog files (see Step 3 above)
- [ ] Push to main and verify CI workflow
- [ ] Check that next release includes changelog
- [ ] Start using Conventional Commits format

## 🎉 You're All Set!

The automated changelog system is now fully configured. Every release will automatically:
- Update CHANGELOG.md with categorized changes
- Include changelog in GitHub Release notes
- Provide professional, consistent release documentation

**Next Steps:**
1. Run the backfill script to generate historical changelog
2. Commit and push the changes
3. Start using Conventional Commits format
4. Watch the automation work on the next release!

---

**Questions or Issues?** Check `scripts/README.md` or open a GitHub issue.

## 🔑 Key Point

**Every commit to main automatically triggers a release.** There is no concept of "unreleased" changes - the changelog always reflects actual tagged releases.