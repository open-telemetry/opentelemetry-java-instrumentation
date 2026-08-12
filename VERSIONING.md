# OpenTelemetry Java Instrumentation Versioning

## Compatibility and telemetry stability requirements

Artifacts in this repository follow the compatibility requirements described in the
[OpenTelemetry Java versioning document](https://github.com/open-telemetry/opentelemetry-java/blob/main/VERSIONING.md#compatibility-requirements)
and the OpenTelemetry specification
[versioning and stability document](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/versioning-and-stability.md).

For any stable artifact in this repository, public portions of the artifact must remain backward
compatible, including public Java APIs and user-facing configuration. Backward-incompatible changes
to stable artifacts are only allowed when incrementing the `MAJOR` version number, except for the
following configuration changes:

- Changes to configuration properties that contain the word `experimental` or `preview`.
- Changes to configuration properties under the namespace `otel.javaagent.testing`.

This means that:

- Changes to all other configuration properties are considered breaking changes.
- Changes to telemetry produced by stable instrumentation artifacts in this repository are
  considered breaking unless they are allowed by the OpenTelemetry specification
  [Semantic Conventions Stability](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/versioning-and-stability.md#semantic-conventions-stability)
  requirements. This repository uses those requirements to define breaking and non-breaking
  telemetry changes for stable instrumentation artifacts.

## Stable vs alpha

See <https://github.com/open-telemetry/opentelemetry-java/blob/main/VERSIONING.md#stable-vs-alpha>

IN PARTICULAR:

Not all of our artifacts are published as stable artifacts - any non-stable artifact has the suffix
`-alpha` on its version. NONE of the guarantees described above apply to alpha artifacts. They may
require code or environment changes on every release and are not meant for consumption for users
where versioning stability is important.

## Dropping support for older library versions

### Library instrumentation

Bumping the minimum supported library version for library instrumentation is generally acceptable
if there's a good reason because:

- Users of library instrumentation have to integrate the library instrumentation during build-time
  of their application, and so have the option to bump the library version if they are using an
  unsupported library version.
- Users have the option of staying with the old version of library instrumentation, without being
  pinned on an old version of the OpenTelemetry API and SDK.
- Bumping the minimum library version changes the artifact name, so it is not technically a breaking
  change.

### Javaagent instrumentation

The situation is much trickier for javaagent instrumentation:

- A common use case of the javaagent is applying instrumentation at deployment-time (including
  to third-party applications), where bumping the library version is frequently not an option.
- While users have the option of staying with the old version of javaagent, that pins them on
  an old version of the OpenTelemetry API and SDK, which is problematic for the OpenTelemetry
  ecosystem.
- While bumping the minimum library version changes the instrumentation module name, it does not
  change the "aggregated" javaagent artifact name which most users depend on, so could be considered
  a breaking change for some users (though this is not a breaking change that we currently make any
  guarantees about).

For these reasons, bumping the minimum supported library version for a javaagent instrumentation
requires more scrutiny and must be considered on a case-by-case basis.

When there is functionality in a new library version that requires changes to the javaagent
instrumentation which are incompatible with the current javaagent base library version, some options
that do not require bumping the minimum supported library version include:

- Access the new functionality via reflection. This is a good technique only for very small changes.
- Create a new javaagent instrumentation module to support the new library version. This requires
  configuring non-overlapping versions in the muzzle check and applying `assertInverse` to confirm
  that the two instrumentations are never be applied to the same library version (see
  [class loader matchers](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/writing-instrumentation-module.md#restrict-the-criteria-for-applying-the-instrumentation-by-extending-the-classloadermatcher-method)
  for how to restrict instrumentations to specific library versions). If there's too much copy-paste
  between the two javaagent instrumentation modules, a `-common` module can be extracted.
