# OpenTelemetry Javaagent Instrumentation: Spring Security Config

Javaagent automatic instrumentation to capture `enduser.*` semantic attributes
from Spring Security `Authentication` objects.

## Settings

This module honors the [common `otel.instrumentation.common.enduser.*` properties](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/#common-instrumentation-configuration)
and the following properties:

| Property                                                                      | Type    | Default  | Description                                                                                             |
|-------------------------------------------------------------------------------|---------|----------|---------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.spring-security.enduser.role.granted-authority-prefix`  | String  | `ROLE_`  | Prefix of granted authorities identifying roles to capture in the `enduser.role` semantic attribute.    |
| `otel.instrumentation.spring-security.enduser.scope.granted-authority-prefix` | String  | `SCOPE_` | Prefix of granted authorities identifying scopes to capture in the `enduser.scopes` semantic attribute. |
