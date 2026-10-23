# Recorded results

The reported startup failure reproduced on Java 8 with released artifacts. It required two deployments in the tested setup. Merely adding a second Spring version to Tomcat did not make a single WAR fail.

These results establish one reproducing deployment, not the configuration of the reported application. Its exact Spring, Tomcat, and agent versions remain unknown.

## Environment and matrix

Recorded on 2026-10-23 using Docker Desktop's Linux containers on Windows, Docker Engine 29.9.2, linux/amd64, external Tomcat 9.0.68, and Temurin `1.8.0_352-b08`. Maven 3.9.9 built the WAR in a separate Java 8 container. Spring artifacts came from Maven Central. Agent 2.33.0 was the [current released agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/tag/v2.33.0) at investigation time. All three released agents actually started on this Java 8 runtime.

Every cell below used a fresh disposable container. `200` means `/hello` returned exactly `hello from Spring MVC` plus a newline, `/origins` returned 200, and the expected presence or absence of the agent bean matched. `404 / target` means the second context failed to start with the specific RequestPath constraint and all four required stack markers.

| Deployment | No agent | 1.33.0 | 2.10.0 | 2.10.0, indy=true | 2.33.0 |
| --- | --- | --- | --- | --- | --- |
| Spring 5.3.39 inside ordinary WAR, no common Spring | 200 | 200 | 200 | 200 | 200 |
| Spring 5.3.39 entirely in common loader | 200 | 200 | 200 | 200 | 200 |
| WAR 5.3.39, common 5.2.25.RELEASE, child first | 200 | 200 | 200 | 200 | 200 |
| WAR 5.2.25.RELEASE, common 5.3.39, child first, only `/probe` deployed | 200 | 200 | 200 | 200 | 200 |
| Same older WAR first, then common-only Spring 5.3.39 WAR | 200 | 404 / target | 404 / target | 404 / target | 404 / target |
| Same two WARs, `/probe` uses parent-first delegation | 200 | 200 | 200 | 200 | 200 |

In both two-WAR rows, `/probe/hello` returned 200 before the second deployment for every agent setting. Without an agent, `/shared/hello` also returned 200 and both contexts reported `otelAutoDispatcherFilter=false`. In the failing cases, `/shared/hello` returned Tomcat's ordinary 404 page because `/shared` failed during listener initialization. The checker did not request its diagnostics endpoint.

The [README commands](README.md) recreate all six rows. Results directories `final-war`, `final-common`, `final-duplicate`, `final-older-war`, `final-shared-after`, and `final-parent-first-shared` held the full logs and metadata for this matrix. The runner saves these locally and does not commit container-specific metadata or megabytes of class-loading logs.

## Exact failure and class origins

Agent 2.10.0 produced this cause before any diagnostic request in the two-WAR reproduction:

```text
Caused by: java.lang.LinkageError: loader constraint violation: loader (instance of java/net/URLClassLoader) previously initiated loading for a different type with name "org/springframework/http/server/RequestPath"
    at java.lang.ClassLoader.defineClass1(Native Method)
    ...
    at java.lang.Class.forName(Class.java:264)
    at org.springframework.web.servlet.v3_1.OpenTelemetryHandlerMappingFilter.parseAndCacheMh(OpenTelemetryHandlerMappingFilter.java:190)
    at org.springframework.web.servlet.v3_1.OpenTelemetryHandlerMappingFilter.<clinit>(OpenTelemetryHandlerMappingFilter.java:45)
```

The enclosing exception was `BeanCreationException: Error creating bean with name 'otelAutoDispatcherFilter'`. Its stack contained `ContextLoaderListener.contextInitialized(ContextLoaderListener.java:103)`. Tomcat logged `Context [/shared] startup failed due to previous errors`. Agents 1.33.0, 2.10.0 with indy enabled, and 2.33.0 produced the same failure category and startup markers.

Representative actual defining loaders from the agent 2.10.0 reproduction:

| Class observed through `/probe/origins` after the failed second startup | Defining loader | Code source |
| --- | --- | --- |
| `ContextLoaderListener` | `ParallelWebappClassLoader@1d3d76b4` | `webapps/probe/WEB-INF/lib/spring-web-5.2.25.RELEASE.jar` |
| `DispatcherServlet` | `ParallelWebappClassLoader@1d3d76b4` | `webapps/probe/WEB-INF/lib/spring-webmvc-5.2.25.RELEASE.jar` |
| `ServletRequestPathUtils` | `java.net.URLClassLoader@40c06358` | `lib/spring-web-5.3.39.jar` |
| `RequestPath` | `ParallelWebappClassLoader@1d3d76b4` | `webapps/probe/WEB-INF/lib/spring-web-5.2.25.RELEASE.jar` |
| `OpenTelemetryHandlerMappingFilter` | `ParallelWebappClassLoader@1d3d76b4` | `/opt/agents/2.10.0.jar` |

All relative code sources in this table are under `/usr/local/tomcat`. The first application's listener recorded that webapp loader's parent as `java.net.URLClassLoader@40c06358`. The second application's listener recorded a different webapp loader, `ParallelWebappClassLoader@171c4a8d`, with the same common-loader parent.

In the no-agent two-WAR control, `/shared/origins` reported all four Spring classes from `lib/spring-*-5.3.39.jar`, defined by `java.net.URLClassLoader@6267c3bb`. With parent-first delegation and agent 2.10.0, `/probe/origins` reported all four Spring classes and the agent filter through `java.net.URLClassLoader@66e21568`; the Spring code sources were the common 5.3.39 JARs. These identities are per-process observations, not stable values to assert on reruns.

Ordinary WAR deployments kept the Spring classes and injected helper in one `ParallelWebappClassLoader`. Common-only deployments kept them in one `java.net.URLClassLoader`. The newer WAR with older common dependencies still resolved the relevant classes together in its webapp loader.

## Released implementation and explanation

Spring's [`ServletRequestPathUtils` in 5.3.39](https://github.com/spring-projects/spring-framework/blob/v5.3.39/spring-web/src/main/java/org/springframework/web/util/ServletRequestPathUtils.java) has `@since 5.3`, and `parseAndCache(HttpServletRequest)` returns `org.springframework.http.server.RequestPath`. Spring 5.2.25.RELEASE has `RequestPath` but no `ServletRequestPathUtils`.

The released agent helper in [1.33.0](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v1.33.0/instrumentation/spring/spring-webmvc/spring-webmvc-3.1/javaagent/src/main/java/org/springframework/web/servlet/v3_1/OpenTelemetryHandlerMappingFilter.java), [2.10.0](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.10.0/instrumentation/spring/spring-webmvc/spring-webmvc-3.1/javaagent/src/main/java/org/springframework/web/servlet/v3_1/OpenTelemetryHandlerMappingFilter.java), and [2.33.0](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.33.0/instrumentation/spring/spring-webmvc/spring-webmvc-3.1/javaagent/src/main/java/org/springframework/web/servlet/v3_1/OpenTelemetryHandlerMappingFilter.java) does the following in a static initializer:

1. Load `ServletRequestPathUtils` and `RequestPath` with one-argument `Class.forName`, using the helper's defining loader.
2. Find the static `parseAndCache` method with a method type built from that `RequestPath` class and the servlet request class.
3. Catch missing-class, missing-method, and illegal-access exceptions, but not `LinkageError`.

The agent's [2.10.0 bean registration advice](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.10.0/instrumentation/spring/spring-web/spring-web-3.1/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/springweb/v3_1/WebApplicationContextInstrumentation.java) loads the helper from `DispatcherServlet`'s loader and registers its class as a singleton bean. Spring then instantiates that bean during root-context initialization.

In the older child-first WAR, the new utility exists only in the parent, while `RequestPath` exists locally. The helper's method-handle lookup therefore crosses defining loaders for the same signature. When the later common-only application initializes its own helper, `Class.forName` tries to load the common loader's `RequestPath`. The JVM rejects that definition because the loader has already initiated loading for a different `RequestPath` type. The exception escapes static initialization into Spring bean creation. This is the failure recorded above, not an exception thrown by application code.

Agent 2.10.0's [`ExperimentalConfig.indyEnabled()`](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.10.0/javaagent-extension-api/src/main/java/io/opentelemetry/javaagent/bootstrap/internal/ExperimentalConfig.java) reads `otel.javaagent.experimental.indy`. However, its [Spring MVC module](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.10.0/instrumentation/spring/spring-webmvc/spring-webmvc-3.1/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/spring/webmvc/v3_1/SpringWebMvcInstrumentationModule.java) explicitly returns false from `isIndyModule()`. Enabling the global historical option does not move this helper out of the application loader, which agrees with the observed reproduction.

## Unsuccessful and discarded experiments

None of the four single-WAR layouts in the matrix reproduced the startup failure. Parent-first delegation also did not reproduce it. A preliminary reverse-order run, starting the common-only Spring 5.3 application and serving a path-pattern request before deploying the older WAR, returned 200 for both applications with no agent and with agent 2.10.0. Deployment order matters in this reproducer.

An initial application had an invalid empty servlet-context XML document and returned 500 even without an agent. That was an application construction error, not the target failure, and the final schema-qualified XML fixed it. An early diagnostic endpoint inspected `ServletRequestPathUtils` methods reflectively and caused a RequestPath constraint during a diagnostic HTTP request in one mixed-loader setup. The first application had already started and served `/hello`, so that was not the reported startup failure. The final diagnostics do not inspect method signatures, and the reproduction runs above capture failure before any diagnostics.

The original report might involve another application in the container, other framework initialization, or different versions. This investigation does not establish which of those was present. It demonstrates that real released dependencies under Tomcat's standard classloading and deployment rules can produce the exact reported agent-bean initialization failure. It does not show that every duplicate-Spring deployment fails, or establish a production instrumentation fix.
