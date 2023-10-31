# OpenTelemetry Instrumentation: Spring Security Config

Provides a Servlet `Filter` and a WebFlux `WebFilter` to capture `enduser.*` semantic attributes
from Spring Security `Authentication` objects.

Also provides `Customizer` implementations to insert those filters into the filter chains created by
`HttpSecurity` and `ServerHttpSecurity`, respectively.

## Usage in Spring WebMVC Applications

When not using [automatic instrumentation](../javaagent/), you can enable enduser attribute capturing
for a `SecurityFilterChain` by appling an `EnduserAttributesHttpSecurityCustomizer`
to the `HttpSecurity` which constructs the `SecurityFilterChain`.

```java
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet.EnduserAttributesHttpSecurityCustomizer;

@Configuration
@EnableWebSecurity
class MyWebSecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // First, apply application related configuration to http

    // Then, apply enduser.* attribute capturing
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    // Set properties of capturer.  Defaults shown.
    capturer.setEnduserIdEnabled(false);
    capturer.setEnduserRoleEnabled(false);
    capturer.setEnduserScopeEnabled(false);
    capturer.setRoleGrantedAuthorityPrefix("ROLE_");
    capturer.setScopeGrantedAuthorityPrefix("SCOPE_");

    new EnduserAttributesHttpSecurityCustomizer(capturer)
        .customize(http);

    return http.build();
  }
}
```

## Usage in Spring WebFlux Applications

When not using [automatic instrumentation](../javaagent/), you can enable enduser attribute capturing
for a `SecurityWebFilterChain` by appling an `EnduserAttributesServerHttpSecurityCustomizer`
to the `ServerHttpSecurity` which constructs the `SecurityWebFilterChain`.

```java
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux.EnduserAttributesServerHttpSecurityCustomizer;

@Configuration
@EnableWebFluxSecurity
class MyWebFluxSecurityConfig {

  @Bean
  public SecurityWebFilterChain filterChain(ServerHttpSecurity http) throws Exception {
    // First, apply application related configuration to http

    // Then, apply enduser.* attribute capturing
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    // Set properties of capturer.  Defaults shown.
    capturer.setEnduserIdEnabled(false);
    capturer.setEnduserRoleEnabled(false);
    capturer.setEnduserScopeEnabled(false);
    capturer.setRoleGrantedAuthorityPrefix("ROLE_");
    capturer.setScopeGrantedAuthorityPrefix("SCOPE_");

    new EnduserAttributesServerHttpSecurityCustomizer(capturer)
        .customize(http);

    return http.build();
  }
}
```
