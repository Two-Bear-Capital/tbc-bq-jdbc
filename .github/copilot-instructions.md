# Two Bear Capital — automated code review guidance

Canonical file, maintained in `tbc-platform-workspace` at
`scripts/copilot/copilot-instructions.md` and synced with `make copilot`. Do not hand-edit a
repo's copy.

## Tag every finding `BLOCKING:` or `ADVISORY:`

Start each finding with one of these two words. They decide whether the PR can merge, so
the tag is part of the finding, not decoration.

- **`BLOCKING:`** — wrong behaviour, a security hole, a secret or PII crossing into a wider
  trust domain, a data-layer boundary violation, changed logic shipped with no test, or new
  duplication of logic that already has a single home.
- **`ADVISORY:`** — everything else: stale prose, naming, structure, a cleanup worth doing
  later.

Only blocking threads gate the merge. Advisory findings are **not** discarded — every
unresolved one is filed automatically as a tracked issue when the PR merges, so file an
advisory finding freely rather than inflating it to blocking to make sure it survives.

Judge by consequence, not by confidence. "This might be wrong and if it is the write
silently corrupts a record" is blocking; "this name could be clearer" is advisory however
certain you are.

## Post every finding — never suppress one

If you are confident enough to write a finding down, post it as a review comment on the
diff. **Do not collapse findings into a "Suppressed comments" section.**

This is measured, not hypothetical: over the last 30 days, **26% of PRs here had at least
one suppressed Copilot finding**. They were not nitpicks. One of them was a `dry_run` flag
that only reached one of four mutating calls, so a run advertised as making no writes
uploaded files, queued parses and wrote audit notes. Because it was collapsed, the author's
fix missed it, and the other reviewer re-found it a round later — which is the exact extra
round this guidance exists to prevent.

A low-confidence finding is an `ADVISORY:` finding. It is not a hidden one.

## Spend the review where the risk is

Review effort should land on **code that can behave wrongly**, in roughly this order:

1. **Correctness and security in the executable code** — wrong results, unhandled failure
   paths, injection, a secret or PII reaching a wider trust domain, a guard that cannot fire.
   Where that code lives is repo-dependent: `src/` in most, but `scripts/` in the workspace
   meta-repo, `models/` in the dbt repo, `terraform/` in the infra repo. Judge by what runs,
   not by the directory name.
2. **Missing or weak tests for changed logic** — a change to behaviour without a test is a
   finding. So is a test whose name claims more than its assertions check.
3. **DRY violations** — new duplication of logic that has a single home.
4. **Prose accuracy**, under the limits below.

## Report the defect class, not each instance

Before reporting a defect, **sweep for every other instance of the same class** — the rest of
the file, the sibling files, the repo. Report them as **one** finding that lists every site.

If the defect is a design flaw whose symptoms will keep recurring — a guard keyed on the shape
of a value instead of proof of where it came from, a state machine whose branches can
contradict each other, a hand-written list standing in for a live system's real values — say
so and name the **class-level** fix. Do not propose only the local patch.

This is the single most expensive habit measured here: **16% of all findings over 30 days
existed only because of the fix for the previous finding.** A reviewer who reports the class
turns four rounds into one; a reviewer who reports the instance guarantees the next round.

The canonical statement of this rule — what it was measured to cost, the worked examples, and
the author's half of it — lives in `conventions/engineering-judgment.md` ("Fix the class, not
the instance"). It is summarised here rather than copied because this file is synced into 15
repos, and a restated enumeration is the most drift-prone shape there is (see
`comments-and-docs.md`).

## Comments and documentation

These repos comment heavily on purpose, so prose is often most of a diff. Two rules keep review
of it proportionate:

- **Flag a comment or doc only when the code contradicts it** — a stale reference, or a claim
  about behaviour that the diff itself or the code it describes makes false. That is a real
  defect and worth reporting.
- **Do not flag wording, tone, length or phrasing preference.** If the statement is true, leave
  it.

⚠️ **Report drift once, structurally.** When the same fact is restated in several places and
one has gone stale, the finding is *"this fact is stated in N places; consolidate to one and
link"* — filed once. Do not file one finding per restatement site across successive rounds: it
turns a single structural problem into many rounds of the same conversation, and the fix for
the instance does not prevent the next one.

## Do not

- Re-review a **canonical-file sync PR** (byte-identical copies of files reviewed once at their
  source in `tbc-platform-workspace`).
- Restate unchanged code, or nitpick formatting the formatter owns.
- Invent findings when a PR is clean. Say it is clean.
