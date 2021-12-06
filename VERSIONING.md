# OpenTelemetry Java Instrumentation Versioning

## Compatibility requirements

Artifacts in this repository follow the same compatibility requirements described in
https://github.com/open-telemetry/opentelemetry-java/blob/main/VERSIONING.md#compatibility-requirements
.

EXCEPT for the following incompatible changes which are allowed in stable artifacts in this
repository:

* Changes to the telemetry produced by instrumentation
  (there will be some guarantees about telemetry stability in the future, see discussions
  in https://github.com/open-telemetry/opentelemetry-specification/issues/1301)
* Changes to configuration properties that contain the word `experimental`
* Changes to configuration properties under the namespace `otel.javaagent.testing`

This means that:

* Changes to configuration properties (other than those that contain the word `experimental`
  or are under the namespace `otel.javaagent.testing`) will be considered breaking changes
  (unless they only affect telemetry produced by instrumentation)

## Stable vs alpha

See https://github.com/open-telemetry/opentelemetry-java/blob/main/VERSIONING.md#stable-vs-alpha

IN PARTICULAR:

Not all of our artifacts are published as stable artifacts - any non-stable artifact has the suffix
`-alpha` on its version. NONE of the guarantees described above apply to alpha artifacts. They may
require code or environment changes on every release and are not meant for consumption for users
where versioning stability is important.

## Dropping support for older library versions

### Library instrumentation

Bumping the minimum supported library version for library instrumentation is generally acceptable
if there's a good reason because:

* Users of library instrumentation have to integrate the library instrumentation during build-time
  of their application, and so have the option to bump the library version if they are using an
  unsupported library version.
* Users have the option of staying with the old version of library instrumentation, without being
  pinned on an old version of the OpenTelemetry API and SDK.
* Bumping the minimum library version changes the artifact name, so it is not technically a breaking
  change.

### Javaagent instrumentation

The situation is much trickier for javaagent instrumentation:

* A common use case of the javaagent is applying instrumentation at deployment-time (including
  to third-party applications), where bumping the library version is frequently not an option.
* While users have the option of staying with the old version of javaagent, that pins them on
  an old version of the OpenTelemetry API and SDK, which is problematic for the OpenTelemetry
  ecosystem.
* While bumping the minimum library version changes the instrumentation module name, it does not
  change the "aggregated" javaagent artifact name which most users depend on, so could be considered
  a breaking change for some users (though this is not a breaking change that we currently make any
  guarantees about).

For these reasons, bumping the minimum supported library version for a javaagent instrumentation
requires more scrutiny and must be considered on a case-by-case basis.

When there is functionality in a new library version that requires changes to the javaagent
instrumentation which are incompatible with the current javaagent base library version, some options
that do not require bumping the minimum supported library version include:

* Access the new functionality via reflection. This is a good technique only for very small changes.
* Create a new javaagent instrumentation module to support the new library version. This requires
  configuring non-overlapping versions in the muzzle check and applying `assertInverse` to confirm
  that the two instrumentations are never be applied to the same library version (see
  [class loader matchers](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/writing-instrumentation-module.md#restrict-the-criteria-for-applying-the-instrumentation-by-extending-the-classloadermatcher-method)
  for how to restrict instrumentations to specific library versions). If there's too much copy-paste
  between the two javaagent instrumentation modules, a `-common` module can be extracted.
