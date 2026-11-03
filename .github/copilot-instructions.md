# OpenTelemetry Java Instrumentation rules

These rules and the applicable `.github/instructions/*.instructions.md` files guide both
implementation and code review. Apply a rule only when its stated conditions hold. Keep its
exceptions, rationale, and useful examples in the same topic file, not in a second guidance catalog.

## Correctness and compatibility

- Preserve lifecycle ownership, reentrancy, concurrency, error handling, and telemetry under
  supported library usage. Trace relevant callers, configuration, initialization, registration,
  and tests rather than reasoning only from edited syntax. Do not add defenses for unsupported
  hypothetical failures.
- Verify library and framework assumptions against the supported dependency versions and concrete
  implementations. Check comments and copied code against the behavior they describe.
- For a public Java API change, identify the *publishing artifact* and determine API status
  from its published contract and intended external use, not source path or visibility alone.
  Symbols explicitly documented as internal, unsupported, and subject to change are not supported
  public APIs merely because their artifact is published; implementation-only changes to them need
  no public-API deprecation cycle or CHANGELOG entry. This does not exempt `.internal` packages or
  `Experimental` helpers generally. Javaagent modules bundled into the agent are not published
  Java APIs. In stable artifacts (`otel.stable=true` in that module's `gradle.properties`),
  deprecate before removal and keep deprecated symbols until the next major version. Published
  alpha artifacts may remove deprecated symbols in a later minor release. Determine stability from
  the publishing module, not a class name or a memorized artifact list. Do not mistake a 3.0
  behavior migration for an alpha API that should be removed immediately. A replacement must be
  at least as stable as the old API; deprecated methods delegate to their replacements, not vice
  versa. Include the replacement and removal timing in `@deprecated` Javadoc. Use plain
  `@Deprecated` for Java-8-compatible APIs, without `since` or `forRemoval`.
- A deprecation scheduled for a major-version behavior change keeps that timing even in an alpha
  artifact. Examples include semconv migrations, interface overrides following an upstream removal,
  and the flat `ConfigProperties` bridge.
- User-facing configuration names and outgoing telemetry identities also have compatibility
  contracts. When a module is renamed, inspect both its `otel.instrumentation.<name>.enabled`
  alias and emitted `otel.scope.name`, including scope-version lookup.
  Preserving one does not preserve the other. Keep version lookup aligned with the name passed to
  `Instrumenter.builder(...)`. Assess telemetry changes under `VERSIONING.md`, including the
  changes that the adopted semantic conventions permit; not every telemetry edit is breaking.
- For any instrumentation module changed, compare new or changed configuration reads, types, and
  defaults with its `metadata.yaml`, including settings read in a dependent common module. The general
  module enable/disable property is not a metadata configuration entry.

For example, an alpha API deprecation delegates to its replacement:

```java
/**
 * @deprecated Use {@link #newMethod()} instead. May be removed in the next minor release.
 */
@Deprecated
public ReturnType oldMethod() {
  return newMethod();
}
```

For a stable API, the Javadoc states removal in the next major version instead.

## Documentation

- Document flat properties without repeating equivalent declarative configuration.
  Include declarative configuration only when it has different behavior, requires a distinct
  migration, or supports configuration unavailable through flat properties.
- Add hand-written changelog entries only for deprecations or breaking changes, as specified in
  CONTRIBUTING.md; normal changes rely on the generated changelog. Use the appropriate stable or
  non-stable breaking-change heading and the corresponding PR label. Keep entries short, naming a
  deprecated interface and its replacement without repeating removal timing or obvious migration
  steps. Describe the released behavior directly, without
  referring to preview flags used to stage the change. Omit statements about behavior that is
  unchanged unless they are necessary to clarify the change's scope.
- Add README and usage documentation for a concrete reader need: setup,
  configuration, API usage, or a non-obvious user-visible limitation or privacy constraint.
  A code change alone does not require a README addition. Do not add prose merely to inventory
  automatically emitted attributes or schema URLs, narrate internal build or instrumentation
  mechanics, or list capabilities an API does not provide without a relevant usage consequence.
  Keep required supported-library entries, configuration tables, public API Javadoc, and
  necessary usage and compatibility guidance.
- Update the existing settings table, API documentation, or other appropriate reference instead
  of repeating it in extra paragraphs or multiple module and parent READMEs. Reuse existing
  metadata and generated catalogs for telemetry inventories when they already cover the detail.
  Targeted source comments and contributor documentation remain appropriate for non-obvious
  implementation rationale or development workflows.
- Keep migration notes in CHANGELOG.md, not READMEs or other documentation.
  READMEs and usage documentation should describe current supported behavior without migration
  sections or historical comparisons. PR descriptions may include migration guidance, old/new
  examples, and removed properties or APIs.

## When reviewing

- Review changed behavior, tracing the relevant callers, configuration, lifecycle, and tests.
  Report a concrete defect or an explicit repository convention violated on a changed line.
  Include the supported failure mode or the violated convention and an actionable fix.
  If the evidence or an exception is unclear, do not comment.
- Do not report pre-existing issues, speculative risks, or failures CI surfaces directly:
  compilation, Spotless formatting, Checkstyle, ErrorProne, NullAway, or failing tests.
  Missing coverage for behavior CI does not exercise can still matter. Do not ask the author
  to run a formatter.
- For unchanged metadata, report only demonstrable mismatches introduced by the change.
- Use category tags such as `[General]`, `[Javaagent]`, `[Config]`, `[Testing]`, and `[Naming]`.
