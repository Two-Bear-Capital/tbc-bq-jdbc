# Release Branches

How a milestone's work is grouped into a single release.

## Why they exist

`version-and-release.yml` runs after every successful Build of a non-bump commit
on `main`, and cuts a release each time. Merging a milestone's eight PRs straight
to `main` therefore produces eight tags, eight changelogs and eight Maven Central
publishes — one per PR, none of which is the release anyone wanted.

A `release/<version>` branch collects them. Nothing releases while work
accumulates there, because the release workflow gates on
`workflow_run.head_branch == 'main'`. The branch merges to `main` once, and one
tag comes out carrying the whole milestone.

Used for 3.1.0 (milestone 1, eight issues, PRs #212–#219, merged as #221).

## The flow

```bash
# 1. Branch from main, named for the milestone
git checkout main && git pull
git checkout -b release/3.1.0
git push -u origin release/3.1.0

# 2. Each issue gets its own branch off the release branch
git checkout -b feat/183-functions
# ... work, commit with a Conventional Commits prefix ...
gh pr create --base release/3.1.0 --milestone "3.1.0"

# 3. Squash-merge each PR into the release branch, so one issue is one commit
gh pr merge <n> --squash --delete-branch

# 4. When the milestone is done, one PR to main — see the merge rule below
gh pr create --base main --head release/3.1.0 --milestone "3.1.0"
```

Feature PRs are squash-merged so each issue arrives as one conventional commit.
The release PR is **not**.

## Four things that will bite you

### Merge the release PR with a merge commit, never a squash

`git-cliff` reads the commits since the last tag twice: once to build the
changelog and once to compute the version. A merge commit puts all of the
milestone's commits in that range, so the changelog gets an entry per issue and
the `feat:` commits force the minor bump. Squashing collapses them into one
commit — one changelog line, and a bump derived from whatever prefix that single
subject happens to carry.

`main` permits merge commits and does not require linear history, so this is
allowed. It just has to be the option chosen in the merge dialog, and squash is
the more habitual click.

### `build.yml` must keep its `release/**` triggers

```yaml
on:
  push:
    branches: [ main, develop, 'release/**' ]
  pull_request:
    branches: [ main, 'release/**' ]
```

Without the `pull_request` entry, a PR into a release branch runs **no checks at
all** — it does not fail, it simply reports nothing, and an unreviewed change
merges green. Nothing releases off these branches regardless, because the release
workflow requires `head_branch == 'main'`.

### `Closes #NNN` does not close anything until it reaches `main`

GitHub only acts on closing keywords when the commit or PR lands on the default
branch. Issues stay open for the whole milestone no matter what the feature PRs
say, which is expected and not a sign anything went wrong.

List every `Closes #NNN` in the **release PR body** so merging it closes the set.
Relying on the keywords inside the squashed commit messages works too, but only
if the final merge preserves them — one more reason not to squash it.

### Merge `main` back in if anything else lands there

A hotfix or a dependabot merge to `main` mid-milestone cuts its own release and
bumps `pom.xml`, leaving the release branch behind. Merge `main` into the release
branch before the final PR. `cliff.toml` skips `^Merge branch`, so those merges
never reach the changelog.

## Naming

`release/<version>` matches the milestone it drains: milestone 3.1.0 →
`release/3.1.0`. The version is a prediction until the tag exists — `git-cliff`
derives the real number from the commit prefixes and never reads the milestone —
so treat a mismatch as cosmetic rather than something to chase.
