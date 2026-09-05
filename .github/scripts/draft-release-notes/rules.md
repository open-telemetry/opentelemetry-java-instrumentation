# Classification rules

Single source of truth for the draft-release-notes skill. Read by
`classify.py` (embedded verbatim into the per-PR LLM prompt) and by
humans during the finalize step.

## Schema

Respond with a single JSON object matching exactly this schema and
nothing else (no prose). A surrounding `json` code fence is tolerated
by the parser but discouraged — prefer a bare JSON object:

```text
{
  "decision": "include" | "omit",
  "section": "breaking" | "deprecations" | "new-javaagent" | "new-library" | "enhancements" | "bug-fixes" | null,
  "surface": "<short phrase describing what the diff changes>",
  "user_visible_effect": "<one sentence a user notices after upgrade, or 'none' if omit>",
  "bullet": "<final CHANGELOG sentence without the PR link, or null if omit>",
  "evidence": "<2-4 line verbatim quote from the diff that justifies the decision>"
}
```

The response must be parseable by `json.loads`. JSON-escape every special
character in string values, including `"` as `\"`, `\` as `\\`, and newlines
as `\n`. Pay particular attention to Java string literals copied into
`evidence`, and verify the complete object is valid JSON before responding.

## Core rule

Classify every PR from its diff only. The classifier removes `CHANGELOG.md`
changes before sending the diff to the model, so existing hand-written entries
cannot steer the generated result. PR titles, manifest `subject`, scratch-bucket
headings, file lists, and `--stat` summaries are indexing metadata, not
evidence. If the diff and the metadata disagree, the diff wins.

## Breaking changes to non-stable APIs

Removes or changes the signature of a non-private method, class, or
interface in a non-stable (`-alpha`) module or in `javaagent-extension-api`
/ `*/internal/**`. Includes:

- removal of a non-`@Deprecated` method,
- removal of a `default` method from an internal interface,
- signature change even when the method never carried `@Deprecated`.

Omit compatibility changes to APIs introduced after the previous release. If
the prompt's release context says the API-diff snapshot marks both signatures
as new, and changing that API is the PR's only user-visible effect, the
decision must be `omit`.

Treat non-private `Experimental*` helpers in published `:library`
artifacts as incubating public API even when their package name
contains `.internal`; removals or binary-incompatible reshaping belong
under Breaking.

Emitted-attribute, attribute-value, or span-name changes are Breaking
**only** when they ship unconditionally. If the change is gated behind
`otel.instrumentation.common.v3-preview`,
`otel.semconv-stability.opt-in=…`, or an `experimental` property, the
entry belongs under Enhancements. Unconditional changes to a metric
attribute value that fix unbounded cardinality belong under Bug fixes —
see that section.

Deprecate-then-remove across two PRs in one cycle produces two bullets —
one under Deprecations, one under Breaking.

## Deprecations

Adds `@Deprecated` to a user-facing API, or renames a config property /
YAML key while keeping the old one. Name both the old and new user-facing
flat property; include the YAML key when relevant.

If a PR both adds replacement functionality and deprecates the old surface,
classify it under Deprecations and describe the migration, not the new feature.
Name every user-facing property, API, configuration key, and artifact that the
PR newly deprecates, along with the replacement for each.

Configuration property renames always go here, never in Enhancements.
Stability policy:

- Stable property/API: may be deprecated in any minor; removable only in
  a major.
- Experimental property (name contains `experimental` or YAML key ends
  with `/development`): may be deprecated in one release and removed in
  the next.

Omit changes that only adjust the planned removal version or wording for APIs
that are already deprecated.

Every deprecation bullet must begin with `Deprecate` and use the form
`Deprecate <old> in favor of <replacement>.` Do not mention when the deprecated
surface may be removed. Do not restate how the deprecated surface behaves
unless that behavior is needed to migrate to the replacement.
For span suppression, name the programmatic
`Experimental.setSpanSuppressionStrategy(...)` replacement without adding
declarative instrumentation configuration as another alternative.

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

Never cite `otel.semconv-stability.preview`; it is an internal implementation
name, not a user-facing property. Translate it to
`otel.semconv-stability.opt-in=<value>`.

## Bug fixes

Wrong attributes, missing spans, NPE/leak/deadlock fixes, latest-dep
compatibility, instrumentation-activation fixes (muzzle `versionRange`,
SPI resource names, type matchers), startup ordering, context
propagation, and class-loading fixes. Restoring silently broken
behavior is a bug fix, not an enhancement — diffs that remove an
over-restrictive condition, add a fallback branch, or invert an `&&`
usually belong here. Describe the user-visible symptom.

An unconditional change to a **metric** attribute value is a bug fix, not
a breaking change, when the previous value caused unbounded cardinality —
it varied per process, per instance, or per restart (a per-process
identity hash, a VM-allocated token, a restart-varying counter), so it
could never aggregate into a stable time series.

This exception is limited to metric attribute values. Span names, span
attributes, and log attributes are not covered: they are not aggregated
into time series, so an unstable value there is not a cardinality defect
and the Breaking rule applies. It also does not cover metric attribute
values that were stable but merely inconvenient, renamed, or
reformatted — those remain Breaking.

The bullet must still state that the value changes, so readers who
aggregated on the old series are not surprised at upgrade.

## metadata.yaml is documentation, not evidence

`metadata.yaml` files are static documentation; they don't change
runtime behavior. Treat any change to `metadata.yaml` as describing
existing functionality. Don't emit an Enhancements bullet for a config
property whose only diff evidence is a metadata.yaml entry.

## When to omit

Omit only when the PR's `src/main` runtime changes are entirely limited
to one or more of:

- pure refactor, style, or naming cleanup of non-API surfaces,
- test-only changes, cross-testing, moving tests out of default packages,
- CI/build-tooling with no runtime effect,
- renames of internal (not extension-API) fields, packages, or helpers,
- new package-private, `internal`-package, or test-only methods,
- `metadata.yaml` documentation (see section above).

Do not use the internal-helper omit rule for non-private `Experimental*`
classes in published artifacts; classify their
removal or binary-incompatible reshaping under Breaking.

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
- When behavior is disabled by default or gated by an opt-in, name the exact
  user-facing property and value needed to enable it.
- Do not describe implementation details ("refactored", "moved",
  "simplified") unless that is the user-visible change.
- Do not credit authors.

The merger renders bullets with the PR link on the second line, indented
two spaces:

```text
- Short user-facing description
  ([#NNNN](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/NNNN))
```
