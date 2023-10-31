# OpenTelemetry Javaagent Instrumentation: Spring Security Config

Javaagent automatic instrumentation to capture `enduser.*` semantic attributes
from Spring Security `Authentication` objects.

## Settings

| Property                                                                      | Type    | Default  | Description                                                                                                                         |
|-------------------------------------------------------------------------------|---------|----------|-------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.common.enduser.enabled`                                 | Boolean | `false`  | Whether to capture `enduser.*` semantic attributes.  Must be set to true to capture any `enduser.*` attributes.                     |
| `otel.instrumentation.common.enduser.id.enabled`                              | Boolean | `true`   | Whether to capture `enduser.id` semantic attribute.  Only takes effect if `otel.instrumentation.common.enduser.enabled` is true.    |
| `otel.instrumentation.common.enduser.role.enabled`                            | Boolean | `true`   | Whether to capture `enduser.role` semantic attribute.  Only takes effect if `otel.instrumentation.common.enduser.enabled` is true.  |
| `otel.instrumentation.common.enduser.scope.enabled`                           | Boolean | `true`   | Whether to capture `enduser.scope` semantic attribute.  Only takes effect if `otel.instrumentation.common.enduser.enabled` is true. |
| `otel.instrumentation.spring-security.enduser.role.granted-authority-prefix`  | String  | `ROLE_`  | Prefix of granted authorities identifying roles to capture in the `enduser.role` semantic attribute.                                |
| `otel.instrumentation.spring-security.enduser.scope.granted-authority-prefix` | String  | `SCOPE_` | Prefix of granted authorities identifying scopes to capture in the `enduser.scopes` semantic attribute.                             |
