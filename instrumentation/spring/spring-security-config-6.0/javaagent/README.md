# OpenTelemetry Javaagent Instrumentation: Spring Security Config

Javaagent automatic instrumentation to capture identity semantic attributes
from Spring Security `Authentication` objects.

When explicitly enabled, this instrumentation emits `user.name` and `user.roles` as a string array.
Identity capture is disabled by default.

## Settings

| Property                                                                   | Type    | Default | Description                                                          |
| -------------------------------------------------------------------------- | ------- | ------- | -------------------------------------------------------------------- |
| `otel.instrumentation.common.user.name.enabled`                            | Boolean | `false` | Capture the authenticated user name as `user.name`.                  |
| `otel.instrumentation.common.user.roles.enabled`                           | Boolean | `false` | Capture granted authorities as string-array `user.roles`.            |
| `otel.instrumentation.spring-security.user.roles.granted-authority-prefix` | String  | `ROLE_` | Prefix of granted authorities identifying roles to capture as roles. |
