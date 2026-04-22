# Classification rules

Single source of truth for the draft-release-notes skill. Read by
`classify.py` (embedded verbatim into the per-PR LLM prompt) and by
humans during the finalize step.

## Schema

Respond with a single JSON object matching exactly this schema and
nothing else (no prose). A surrounding `json` code fence is tolerated
by the parser but discouraged — prefer a bare JSON object:

```
{
  "decision": "include" | "omit",
  "section": "breaking" | "deprecations" | "new-javaagent" | "new-library" | "enhancements" | "bug-fixes" | null,
  "surface": "<short phrase describing what the diff changes>",
  "user_visible_effect": "<one sentence a user notices after upgrade, or 'none' if omit>",
  "bullet": "<final CHANGELOG sentence without the PR link, or null if omit>",
  "evidence": "<2-4 line verbatim quote from the diff that justifies the decision>"
}
```

## Core rule

Classify every PR from its diff only. PR titles, manifest `subject`,
draft-script bullet text, scratch-bucket headings, file lists, and
`--stat` summaries are indexing metadata, not evidence. If the diff and
the metadata disagree, the diff wins.

## Breaking changes to non-stable APIs

Removes or changes the signature of a non-private method, class, or
interface in a non-stable (`-alpha`) module or in `javaagent-extension-api`
/ `*/internal/**`. Includes:

- removal of a non-`@Deprecated` method,
- removal of a `default` method from an internal interface,
- signature change even when the method never carried `@Deprecated`.

Emitted-attribute, attribute-value, or span-name changes are Breaking
**only** when they ship unconditionally. If the change is gated behind
`otel.instrumentation.common.v3-preview`,
`otel.semconv-stability.opt-in=…`, or an `experimental` property, the
entry belongs under Enhancements.

Deprecate-then-remove across two PRs in one cycle produces two bullets —
one under Deprecations, one under Breaking.

## Deprecations

Adds `@Deprecated` to a user-facing API, or renames a config property /
YAML key while keeping the old one. Name both the old and new user-facing
flat property; include the YAML key when relevant.

Configuration property renames always go here, never in Enhancements.
Stability policy:

- Stable property/API: may be deprecated in any minor; removable only in
  a major.
- Experimental property (name contains `experimental` or YAML key ends
  with `/development`): may be deprecated in one release and removed in
  the next.

If an unlinked summary bullet at the top of Deprecations already covers
the rename, do not add a duplicate PR-linked bullet.

## New javaagent / library instrumentation

Only for a brand-new module under `instrumentation/<name>/javaagent/**`
or `instrumentation/<name>/library/**` — new `build.gradle.kts`, new
sources, and a new `settings.gradle.kts` entry. Renames or extractions
do not qualify.

## Enhancements

New attributes, new config flags, new stable-semconv support, observable
behavior gated on a flag (`v3-preview`, `SemconvStability`, experimental
property), or measurable hot-path performance improvements. For semconv
opt-ins, cite the flag value (for example
`otel.semconv-stability.opt-in=messaging`) — the known values in this
repo are `database`, `messaging`, `http`, `jvm`, `rpc`. Gated changes go
here, never under Breaking.

## Bug fixes

Wrong attributes, missing spans, NPE/leak/deadlock fixes, latest-dep
compatibility, instrumentation-activation fixes (muzzle `versionRange`,
SPI resource names, type matchers), startup ordering, context
propagation, and class-loading fixes. Restoring silently broken
behavior is a bug fix, not an enhancement — diffs that remove an
over-restrictive condition, add a fallback branch, or invert an `&&`
usually belong here. Describe the user-visible symptom.

## When to omit

Omit only when the PR's `src/main` runtime changes are entirely limited
to one or more of:

- pure refactor, style, or naming cleanup of non-API surfaces,
- test-only changes, cross-testing, moving tests out of default packages,
- CI/build-tooling with no runtime effect,
- renames of internal (not extension-API) fields, packages, or helpers,
- new package-private, `internal`-package, or test-only methods.

Trivial omits (renovate bumps, all-test/docs/build paths, post-release
version bumps) are handled by `classify.py --preclassify-only`.
Everything else must be decided from the diff on a per-PR basis.

Omit reasons that lean on appearance words — "probably internal",
"mostly plumbing", "looks like refactor", "reads as tooling", "diff is
dominated by X" — are not acceptable while `src/main` runtime code is
touched. Re-read the diff and write a concrete user-visible effect, or
keep the PR.

## Bias toward keeping when the diff touches

- Emitted telemetry: new attributes, gated-behavior changes, schema URL
  changes, new `SemconvStability.emitStable…` branches.
- Startup, context propagation, class loading, or lifecycle behavior
  that can disable telemetry, leak memory, deadlock, or otherwise break
  normal operation (removal of an early `GlobalOpenTelemetry.get()`
  call; closing bridged callbacks on GC; fixing an agent deadlock).
- Agent transformation correctness: `@Advice` inline vs indy, advice
  scope, helper-class exposure to the application class loader.
- Any public or extension-facing API, builder method, config key, or
  semconv surface, even when the diff also includes plumbing.

## Bullet style

- One sentence per bullet.
- Name concrete user-facing surfaces: flag names, property names, class
  names, attribute names. Use backticks for config keys, property names,
  attributes, and class/method names.
- For `v3-preview`-gated changes, cite the user-facing property name
  `otel.instrumentation.common.v3-preview`, not the internal
  `v3_preview` key.
- Do not describe implementation details ("refactored", "moved",
  "simplified") unless that is the user-visible change.
- Do not credit authors.

The merger renders bullets with the PR link on the second line, indented
two spaces:

```
- Short user-facing description
  ([#NNNN](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/NNNN))
```

Grouping multiple PRs into one logical bullet is done by hand after
merging — edit `CHANGELOG.md` directly to combine trailing PR links, or
set identical `bullet` text on each `decision.json` and collapse by hand
after running the merger.
