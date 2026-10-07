# OpenTelemetry Instrumentation: Spring Security Config

Provides a Servlet `Filter` and a WebFlux `WebFilter` to capture identity semantic attributes
from Spring Security `Authentication` objects.

When explicitly enabled, this instrumentation emits `user.name` and `user.roles` as a string array.
Identity capture is disabled by default. Scope authorities are not captured.

For javaagent users, replace `otel.instrumentation.common.enduser.id.enabled` and
`otel.instrumentation.common.enduser.role.enabled` with
`otel.instrumentation.common.user.name.enabled` and
`otel.instrumentation.common.user.roles.enabled`, respectively. Replace
`otel.instrumentation.spring-security.enduser.role.granted-authority-prefix` with
`otel.instrumentation.spring-security.user.roles.granted-authority-prefix`. The `enduser.scope`
setting has no replacement.

Also provides `Customizer` implementations to insert those filters into the filter chains created by
`HttpSecurity` and `ServerHttpSecurity`, respectively.

## Usage in Spring WebMVC Applications

When not using [automatic instrumentation](../javaagent/), you can enable identity attribute capturing
for a `SecurityFilterChain` by applying a `UserAttributesHttpSecurityCustomizer`
to the `HttpSecurity` which constructs the `SecurityFilterChain`.

```java
import io.opentelemetry.instrumentation.spring.security.config.v6_0.UserAttributesCapturer;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet.UserAttributesHttpSecurityCustomizer;

@Configuration
@EnableWebSecurity
class MyWebSecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // First, apply application related configuration to http

    // Then, apply identity attribute capturing
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    // Set properties of capturer. Identity capture is disabled by default.
    capturer.setNameEnabled(false);
    capturer.setRolesEnabled(false);
    capturer.setRoleGrantedAuthorityPrefix("ROLE_");

    new UserAttributesHttpSecurityCustomizer(capturer)
        .customize(http);

    return http.build();
  }
}
```

## Usage in Spring WebFlux Applications

When not using [automatic instrumentation](../javaagent/), you can enable identity attribute capturing
for a `SecurityWebFilterChain` by applying a `UserAttributesServerHttpSecurityCustomizer`
to the `ServerHttpSecurity` which constructs the `SecurityWebFilterChain`.

```java
import io.opentelemetry.instrumentation.spring.security.config.v6_0.UserAttributesCapturer;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux.UserAttributesServerHttpSecurityCustomizer;

@Configuration
@EnableWebFluxSecurity
class MyWebFluxSecurityConfig {

  @Bean
  public SecurityWebFilterChain filterChain(ServerHttpSecurity http) throws Exception {
    // First, apply application related configuration to http

    // Then, apply identity attribute capturing
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    // Set properties of capturer. Identity capture is disabled by default.
    capturer.setNameEnabled(false);
    capturer.setRolesEnabled(false);
    capturer.setRoleGrantedAuthorityPrefix("ROLE_");

    new UserAttributesServerHttpSecurityCustomizer(capturer)
        .customize(http);

    return http.build();
  }
}
```
