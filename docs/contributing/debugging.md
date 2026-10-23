# Debugging

## Instrumentation tests

Run the tests with debugging enabled, then attach your IDE to `localhost:5005`:

```shell
./gradlew :instrumentation:<INSTRUMENTATION_NAME>:test --debug-jvm
```

Most advice is non-inline, so breakpoints work directly in advice methods. For inline advice,
debug its helpers instead.

## Agent startup

Put `-agentlib:` before `-javaagent:` and use `suspend=y` to attach before the agent initializes:

```bash
java -agentlib:jdwp="transport=dt_socket,server=y,suspend=y,address=5005" -javaagent:opentelemetry-javaagent-<version>.jar -jar app.jar
```

Attach your IDE to `localhost:5005` and resume execution.

## Transformed classes

To inspect transformed bytecode, create a dump directory and add this JVM argument.
Decompile the dumped classes to see the instrumentation:

```shell
-Dnet.bytebuddy.dump=/some/path
```

## Shaded classes

Agent shading renames packages, which can prevent IDE breakpoints from matching. For local
debugging, disable it in `~/.gradle/gradle.properties`:

```properties
disableShadowRelocate=true
```

This can make tests fail or pass incorrectly. Use it only for debugging.

## Missing GraalVM hints

Enable the GraalVM tracing agent in `build.gradle.kts`:

```kotlin
graalvmNative {
  agent {
    defaultMode.set("standard")
    enabled.set(true)
  }
}
```

Run `./gradlew nativeTest`. The tracing data is written to `build/native/agent-output`.
