# OpenTelemetry Javaagent Instrumentation: Spring Security Config

Javaagent automatic instrumentation to capture identity semantic attributes
from Spring Security `Authentication` objects.

By default this instrumentation emits the deprecated `enduser.*` attributes when enabled. When
`otel.instrumentation.common.v3-preview` is enabled, it emits `user.name` and the string array
`user.roles` instead, and `enduser.scope` is not supported.

## Settings

This module honors the [common `otel.instrumentation.common.enduser.*` properties](https://opentelemetry.io/docs/zero-code/java/agent/instrumentation/#capturing-enduser-attributes).
When `otel.instrumentation.common.v3-preview` is enabled, it honors
`otel.instrumentation.common.user.name.enabled` and
`otel.instrumentation.common.user.roles.enabled` instead.

It also supports the following properties:

| Property                                                                      | Type   | Default  | Description                                                                                                                                                                                                                       |
| ----------------------------------------------------------------------------- | ------ | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.spring-security.enduser.role.granted-authority-prefix`  | String | `ROLE_` | Prefix of granted authorities identifying roles to capture in the `enduser.role` semantic attribute. This property is not honored when v3 preview is enabled; use the `user.roles` variant instead. |
| `otel.instrumentation.spring-security.user.roles.granted-authority-prefix`    | String | `ROLE_` | Prefix of granted authorities identifying roles to capture in the `user.roles` semantic attribute when v3 preview is enabled. This property is not honored when v3 preview is disabled; use the `enduser.role` variant instead. |
| `otel.instrumentation.spring-security.enduser.scope.granted-authority-prefix` | String | `SCOPE_` | Prefix of granted authorities identifying scopes to capture in the `enduser.scope` semantic attribute. This property and the associated attribute are not supported when v3 preview is enabled. |
