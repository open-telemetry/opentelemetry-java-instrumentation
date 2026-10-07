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

## Migration to 3.0

Replace the previous properties with their current names:

| Previous property                                                            | Current property                                                           |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `otel.instrumentation.common.enduser.id.enabled`                             | `otel.instrumentation.common.user.name.enabled`                            |
| `otel.instrumentation.common.enduser.role.enabled`                           | `otel.instrumentation.common.user.roles.enabled`                           |
| `otel.instrumentation.spring-security.enduser.role.granted-authority-prefix` | `otel.instrumentation.spring-security.user.roles.granted-authority-prefix` |

For example:

```properties
# Before
otel.instrumentation.common.enduser.id.enabled=true
otel.instrumentation.common.enduser.role.enabled=true
otel.instrumentation.spring-security.enduser.role.granted-authority-prefix=ROLE_

# After
otel.instrumentation.common.user.name.enabled=true
otel.instrumentation.common.user.roles.enabled=true
otel.instrumentation.spring-security.user.roles.granted-authority-prefix=ROLE_
```

Telemetry changes from `enduser.id` to `user.name`, and from comma-separated `enduser.role` to
string-array `user.roles`. The `otel.instrumentation.common.enduser.scope.enabled` property and
`enduser.scope` capture have no replacement. The Spring Security
`UserAttributesCapturer.setScopeEnabled(boolean)` and
`UserAttributesCapturer.setScopeGrantedAuthorityPrefix(String)` methods are removed.
