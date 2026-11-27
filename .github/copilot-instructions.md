# OpenTelemetry Java Instrumentation rules

These rules and the applicable `.github/instructions/*.instructions.md` files guide implementation,
audits of existing code, and pull request reviews.

- Apply a rule only when its stated conditions hold.
- Keep a rule's exceptions, rationale, and useful examples in the same topic file, not in a
  second guidance catalog.

## Correctness and compatibility

- Preserve lifecycle ownership, reentrancy, concurrency, error handling, and telemetry under
  supported library usage. Verify these contracts by tracing relevant callers, configuration,
  initialization, registration, and tests. Do not add defenses for unsupported hypothetical failures.

- Verify library and framework assumptions against the supported dependency versions and concrete
  implementations. Check comments and copied code against the behavior they describe.

- Determine whether a Java symbol is supported public API from its *publishing artifact*,
  published contract, and intended external use, not source path or visibility alone.
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

- A deprecation scheduled for a major-version behavior change keeps that timing even in an alpha
  artifact. Examples include semconv migrations, interface overrides following an upstream removal,
  and the flat `ConfigProperties` bridge.

- User-facing configuration names and outgoing telemetry identities have separate compatibility
  contracts. Check supported `otel.instrumentation.<name>.enabled` aliases independently of emitted
  `otel.scope.name`, including for modules that have been renamed. Keep scope-version lookup aligned
  with the name passed to `Instrumenter.builder(...)`. Assess telemetry compatibility under
  `VERSIONING.md` and the adopted semantic conventions; not every telemetry difference is breaking.

- Instrumentation configuration metadata, including each module's `metadata.yaml` and shared
  configuration definitions, matches supported setting names, types, and defaults in the actual
  readers, including readers in dependent common modules. General module enablement
  (`otel.instrumentation.<name>.enabled`) is not an ordinary configuration entry; record a
  default-off module with `disabled_by_default: true`. A documented setting remains in metadata
  while it has a supported reader. Before removing a setting from metadata, look for remaining
  readers in the module and its dependent common modules.

## Documentation

- Document flat properties without repeating equivalent declarative configuration.
  Include declarative configuration only when it has different behavior, requires a distinct
  migration, or supports configuration unavailable through flat properties.
- Use the [changelog instructions](instructions/changelog.instructions.md) to check whether a
  change needs an entry.
- When a change is breaking, add the `breaking change` label to its pull request, as described in
  CONTRIBUTING.md.
- README and usage documentation should meet a concrete reader need: setup,
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

## When auditing existing code

- Apply the relevant rules to all code in the requested scope, including unchanged code and its
  metadata, documentation, and tests. Read related code outside that scope when needed to establish
  the contract. Report concrete violations with their locations, evidence, and an actionable fix.
- If a finding depends on what was previously supported, compare against the relevant released
  artifact, documented contract, or version history. Without that baseline, do not claim a
  compatibility break. Do not treat established exceptions as violations.

## When reviewing a pull request

- Review changed behavior, tracing the relevant callers, configuration, lifecycle, and tests.
  Report only concrete defects or explicit convention violations introduced by the diff, and
  anchor each finding on a changed line. Include the supported failure mode or the violated
  convention and an actionable fix. If the evidence or an exception is unclear, do not comment.
- Do not report pre-existing issues, speculative risks, or failures CI surfaces directly:
  compilation, Spotless formatting, Checkstyle, ErrorProne, NullAway, or failing tests.
  Missing coverage for behavior CI does not exercise can still matter. Do not ask the author
  to run a formatter.
- For unchanged metadata, report only demonstrable mismatches introduced by the change.
