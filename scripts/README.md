# Scripts Documentation

This directory contains utility scripts for maintaining the tbc-bq-jdbc project.

## Changelog Management

### Overview

The project uses [git-cliff](https://git-cliff.org/) for automated changelog generation. Changelogs are automatically generated and committed on each release via GitHub Actions.

### Configuration

- **cliff.toml** - git-cliff configuration in project root
  - Conventional commits format
  - Categorizes changes: Features, Bug Fixes, Performance, Documentation, etc.
  - Skips automated commits (version bumps, dependency updates)
  - Links issues and pull requests automatically

### Backfilling Historical Changelog

To generate a complete changelog for all 52+ historical releases:

```bash
# Install git-cliff (one time)
brew install git-cliff  # macOS
# OR
cargo install git-cliff # Linux/macOS with Rust

# Run backfill script
./scripts/backfill-changelog.sh

# Review the generated CHANGELOG.md
less CHANGELOG.md

# If it looks good, commit it
git add CHANGELOG.md cliff.toml scripts/
git commit -m "chore: add automated changelog generation with git-cliff"
git push
```

**Note:** This should only be run once to create the initial historical changelog. After that, the GitHub Actions workflow will maintain it automatically.

### Automated Changelog Updates

The `.github/workflows/version-and-release.yml` workflow automatically:

1. **Installs git-cliff** on the GitHub Actions runner
2. **Generates changelog entry** for the new release based on commits since last tag
3. **Updates CHANGELOG.md** with the new entry
4. **Commits the updated changelog** before creating the git tag
5. **Includes changelog in GitHub Release** notes for that version

### Manual Changelog Generation

To manually regenerate the changelog:

```bash
# Generate full changelog from all tags
git-cliff --config cliff.toml --output CHANGELOG.md

# Generate changelog for a specific version range
git-cliff --config cliff.toml v1.0.45..v1.0.51
```

### Commit Message Format

To get the most value from automated changelog generation, follow [Conventional Commits](https://www.conventionalcommits.org/):

**Format:** `<type>(<scope>): <description>`

**Types:**
- `feat:` - New feature (shows in "⚡ Features" section)
- `fix:` - Bug fix (shows in "🐛 Bug Fixes" section)
- `perf:` - Performance improvement (shows in "🚀 Performance" section)
- `docs:` - Documentation changes (shows in "📚 Documentation" section)
- `test:` - Test additions/changes (shows in "🧪 Testing" section)
- `refactor:` - Code refactoring (shows in "🔨 Refactoring" section)
- `style:` - Code style changes (shows in "🎨 Styling" section)
- `chore:` - Maintenance tasks (shows in "⚙️ Miscellaneous Tasks" section)
- `ci:` - CI/CD changes (shows in "👷 CI/CD" section)
- `build:` - Build system changes (shows in "🏗️ Build" section)

**Examples:**
```bash
git commit -m "feat(auth): add workforce identity federation support"
git commit -m "fix(metadata): correct column precision for NUMERIC types"
git commit -m "perf(cache): implement parallel dataset loading with virtual threads"
git commit -m "docs(readme): add IntelliJ IDEA quick start guide"
git commit -m "test(integration): add PreparedStatement parameter tests"
```

**Scopes (optional but recommended):**
- `auth` - Authentication
- `metadata` - DatabaseMetaData
- `connection` - Connection management
- `resultset` - ResultSet operations
- `types` - Type mapping
- `cache` - Caching
- `session` - Session management
- `ci` - CI/CD
- `deps` - Dependencies

### Skipped Commits

The following commit patterns are automatically skipped in the changelog:

- `Bump version to X.Y.Z` - Automated version bumps
- `Update documentation to version X.Y.Z` - Automated doc updates
- `chore(deps): ...` - Dependency updates (unless they're significant)
- `Merge branch ...` - Branch merge commits

Pull request merges are included in a "🔀 Pull Requests" section.

### Troubleshooting

**Problem:** `git-cliff: command not found` in CI

**Solution:** The workflow now installs git-cliff automatically. If issues persist, check the installation step in `.github/workflows/version-and-release.yml`.

**Problem:** Changelog is missing recent commits

**Solution:** The workflow automatically creates a release for every commit to main. Commits appear in the changelog after the release workflow completes.

**Problem:** Want to regenerate entire changelog

**Solution:**
```bash
# Backup current changelog
cp CHANGELOG.md CHANGELOG.md.backup

# Regenerate from all tags
git-cliff --config cliff.toml --output CHANGELOG.md

# Review and commit if good
git diff CHANGELOG.md
git add CHANGELOG.md
git commit -m "chore: regenerate changelog"
```

## Future Scripts

Additional maintenance scripts may be added here:

- Database migration scripts
- Performance benchmarking automation
- Release artifact validation
- License header updates
- Code generation utilities

## Contributing

When adding new scripts:

1. Make scripts executable: `chmod +x scripts/your-script.sh`
2. Include usage documentation in script header comments
3. Update this README with script description
4. Use `set -euo pipefail` for bash scripts
5. Provide clear error messages
6. Test scripts in CI environment when applicable