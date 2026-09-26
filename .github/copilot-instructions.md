# OpenTelemetry Java Instrumentation code review

Review the pull request's changed behavior, not just the edited syntax. Trace relevant callers,
configuration, lifecycle, and tests before drawing a conclusion. Report a concrete defect or an
explicit repository convention violated on a changed line; include the failure mode and an
actionable fix. If the evidence or an exception is unclear, do not comment. Use category tags
such as `[General]`, `[Javaagent]`, `[Config]`, `[Testing]`, and `[Naming]`.

Do not report pre-existing issues, speculative risks, or failures CI will surface directly:
compilation, Spotless formatting, Checkstyle, ErrorProne, NullAway, or failing tests. Missing
coverage for behavior CI does not exercise can still matter. Do not ask the author to run a
formatter. Path-specific `.github/instructions/*.instructions.md` files contain the
applicable repository review rules; use their stated conditions and exceptions. The longer
`.github/agents/knowledge/` articles are optional reference, not a required loading step.

## Correctness and compatibility

- Check changes for lifecycle leaks, reentrancy, concurrency under supported library usage,
  unsafe error handling, changed telemetry, incorrect comments, and copy/paste mistakes.
  Identify a supported failure path rather than requesting defenses against hypothetical ones.
- For a changed public Java API, identify the *publishing artifact* before claiming a break.
  Javaagent modules bundled into the agent are not published Java APIs. In stable artifacts
  (`otel.stable=true` in that module's `gradle.properties`), deprecate before removal and keep
  deprecated symbols until the next major version. Published alpha artifacts may remove
  deprecated symbols in a later minor release. Do not mistake a 3.0 behavior migration for an
  alpha API that should be removed immediately. A replacement must be at least as stable as
  the old API; deprecated methods delegate to their replacements, not vice versa. Include
  replacement and removal timing in `@deprecated` Javadoc and a deprecation CHANGELOG entry.
  Document a breaking change to a published alpha API under the appropriate CHANGELOG heading.
- User-facing configuration names and outgoing telemetry identities also have compatibility
  contracts. When a module is renamed, inspect both its `otel.instrumentation.<name>.enabled`
  alias and emitted `otel.scope.name`, including v3-preview behavior and scope-version lookup.
  Preserving one does not preserve the other. Do not require a deprecation cycle for
  implementation-only javaagent symbols.
- For any instrumentation module changed, compare new or changed configuration reads, types, and
  defaults with its `metadata.yaml`, including settings read in a dependent common module. If
  metadata did not change, comment only where the change introduced a demonstrable mismatch. Do
  not request an entry for the general module enable/disable property. Check explicit metadata
  edits under the metadata-specific instructions.

## Configuration

Apply these checks when changed code defines, maps, or reads user-facing configuration, regardless
of where its implementation lives.

- When adding or changing an `otel.instrumentation.*` setting, check both its flat property
  and declarative YAML name. New flat-property segments use kebab-case; new declarative YAML
  keys use snake_case. Experimental or preview names are unstable; an experimental flat name
  must map to the corresponding `/development` YAML form. Do not rename an already-published
  declarative name merely to match these naming rules or mechanical conversion; determine
  whether the bridge needs a `SPECIAL_MAPPINGS` entry instead.
- Stable flat property names remain stable even when read by alpha implementation code.
  On rename, retain the old name until the next major version: read the replacement first,
  fall back to the old name only outside v3-preview, and warn once at startup *when the old
  value is applied*. Add the deprecation to the CHANGELOG. Experimental/preview names may
  be removed after a subsequent minor release and do not need the v3-preview guard.
  Instrumentation enablement aliases have distinct warning semantics; do not apply ordinary
  replacement-first warning logic to them.
- Read module settings from `java.<module>` and general settings from `general`; HTTP
  header capture is general configuration. For a declarative `ComponentProvider`, its
  `getName()` must match the YAML node. If the replacement config value already determines
  an ordinary property's result, merely carrying the deprecated name must not cause a
  warning; deduplicate warnings when reads may repeat.
- In javaagent instrumentation, ordinary instrumentation settings are read through
  `DeclarativeConfigUtil`, with a default for unavailable YAML. A nullable read is intentional
  when probing for a replacement or deprecated name before choosing a default. Flat
  `ConfigProperties` reads are reserved for enablement bootstrapping in
  `AgentDistributionConfig`. Outside javaagent instrumentation, direct reads remain valid when
  required by an SDK SPI or bridge contract. Structured YAML-only settings need declarative-mode
  coverage.
