# Security policy

The [OpenTelemetry security policy](https://github.com/open-telemetry/.github/blob/main/SECURITY.md)
applies to this repository, including its security model and supported-version policy.

## Scope

This policy covers the source code and published artifacts produced by this repository, including:

- The OpenTelemetry Java agent JAR and its bundled instrumentation.
- Maven artifacts, including standalone instrumentation libraries, Spring Boot starters,
  instrumentation and Java agent extension APIs, annotations, and BOMs.
- The Gradle plugins published from this repository.

This repository does not operate a production service or website.
Report vulnerabilities in the OpenTelemetry website to the
[website repository](https://github.com/open-telemetry/opentelemetry.io/security/advisories/new).

## Reporting

For suspected vulnerabilities, use
[Report a vulnerability](https://github.com/open-telemetry/opentelemetry-java-instrumentation/security/advisories/new)
to contact the maintainers privately. Do not disclose vulnerability details in public issues.
If you cannot use GitHub's private reporting workflow, contact
[security@opentelemetry.io](mailto:security@opentelemetry.io).

For non-sensitive security questions or concerns, anyone with a GitHub account can
[open a public issue](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/new/choose).

## Security commitment level

This repository's security commitment level is
[Undeclared](https://github.com/open-telemetry/sig-security/blob/7b709b89616af7ac17964d427570d069f2e54b1e/docs/security-commitments-level.md#undeclared),
as defined in the proposal in
[open-telemetry/sig-security#324](https://github.com/open-telemetry/sig-security/pull/324).
