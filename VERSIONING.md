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
