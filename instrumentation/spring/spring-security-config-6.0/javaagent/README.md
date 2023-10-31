# OpenTelemetry Javaagent Instrumentation: Spring Security Config

Javaagent automatic instrumentation to capture `enduser.*` semantic attributes
from Spring Security `Authentication` objects.

## Settings

| Property                                                                      | Type    | Default  | Description                                                                                             |
|-------------------------------------------------------------------------------|---------|----------|---------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.common.enduser.id.enabled`                              | Boolean | `false`  | Whether to capture `enduser.id` semantic attribute.                                                     |
| `otel.instrumentation.common.enduser.role.enabled`                            | Boolean | `false`  | Whether to capture `enduser.role` semantic attribute.                                                   |
| `otel.instrumentation.common.enduser.scope.enabled`                           | Boolean | `false`  | Whether to capture `enduser.scope` semantic attribute.                                                  |
| `otel.instrumentation.spring-security.enduser.role.granted-authority-prefix`  | String  | `ROLE_`  | Prefix of granted authorities identifying roles to capture in the `enduser.role` semantic attribute.    |
| `otel.instrumentation.spring-security.enduser.scope.granted-authority-prefix` | String  | `SCOPE_` | Prefix of granted authorities identifying scopes to capture in the `enduser.scopes` semantic attribute. |
