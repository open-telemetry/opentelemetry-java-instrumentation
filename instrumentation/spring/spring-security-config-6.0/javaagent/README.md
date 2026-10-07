# OpenTelemetry Javaagent Instrumentation: Spring Security Config

Javaagent automatic instrumentation to capture identity semantic attributes
from Spring Security `Authentication` objects.

When explicitly enabled, this instrumentation emits `user.name` and `user.roles` as a string array.
Identity capture is disabled by default. Scope authorities are not captured.

## Settings

Enable identity capture with `otel.instrumentation.common.user.name.enabled` and
`otel.instrumentation.common.user.roles.enabled`; both default to `false`.

For example, migrate the former settings:

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

The captured keys change from `enduser.id` and comma-separated `enduser.role` to `user.name` and
string-array `user.roles`. The `enduser.scope` setting and scope capture are removed.

The role property configures the authority prefix used to select roles:

| Property                                                                   | Type   | Default | Description                                                          |
| -------------------------------------------------------------------------- | ------ | ------- | -------------------------------------------------------------------- |
| `otel.instrumentation.spring-security.user.roles.granted-authority-prefix` | String | `ROLE_` | Prefix of granted authorities identifying roles to capture as roles. |
