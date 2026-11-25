# Library Instrumentation for Spring Security Config version 6.0 and higher

Provides OpenTelemetry instrumentation for [Spring Security](https://spring.io/projects/spring-security).
A Servlet `Filter` and a WebFlux `WebFilter` capture identity attributes from `Authentication` objects.

When explicitly enabled, this instrumentation emits `user.name` and `user.roles` as a string array.
Identity capture is disabled by default. Scope authorities are not captured.

Also provides `Customizer` implementations to insert those filters into the filter chains created by
`HttpSecurity` and `ServerHttpSecurity`, respectively.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-spring-security-config-6.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-security-config-6.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-security-config-6.0:OPENTELEMETRY_VERSION")
```

### Usage

#### Spring WebMVC applications

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
    // Enable identity capture.
    capturer.setNameEnabled(true);
    capturer.setRolesEnabled(true);
    capturer.setRoleGrantedAuthorityPrefix("ROLE_");

    new UserAttributesHttpSecurityCustomizer(capturer)
        .customize(http);

    return http.build();
  }
}
```

#### Spring WebFlux applications

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
    // Enable identity capture.
    capturer.setNameEnabled(true);
    capturer.setRolesEnabled(true);
    capturer.setRoleGrantedAuthorityPrefix("ROLE_");

    new UserAttributesServerHttpSecurityCustomizer(capturer)
        .customize(http);

    return http.build();
  }
}
```
