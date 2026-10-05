# Debugging

Debugging advice methods depends on whether the advice is inline or non-inline.

## Non-inline advice methods

Non-inline advice methods are annotated with `@Advice.OnMethodEnter(inline = false)` or
`@Advice.OnMethodExit(inline = false)`. Breakpoints work in these methods when running tests:

```
./gradlew :instrumentation:<INSTRUMENTATION_NAME>:test
```

## Inline advice methods

Some instrumentation intentionally uses inline advice. Breakpoints do not work in these methods,
because ByteBuddy copies their code into the target class. Keep these methods as small as possible.
The advice methods are annotated with:

```java
@net.bytebuddy.asm.Advice.OnMethodEnter
@net.bytebuddy.asm.Advice.OnMethodExit
```

Debug the helper methods called by inline advice instead. Changing `inline` can change the
module's helper-loading strategy and is not a safe debugging-only toggle.

When advice is inline, the best approach to debug advice methods and agent initialization is to use the
following statements:

```java
System.out.println();
Thread.dumpStack();
```

Byte Buddy can also output the modified class files to a directory which can be decompiled to see
exactly what changes are taking place. Add the following to your JVM startup arguments with
an existing target directory defined:

```shell
-Dnet.bytebuddy.dump=/some/path
```

## Agent initialization code

If you want to debug agent initialization code (e.g. `OpenTelemetryAgent`, `AgentInitializer`,
`AgentInstaller`, `OpenTelemetryInstaller`, etc.) then it's important to specify the `-agentlib:` JVM arg
before the `-javaagent:` JVM arg and use `suspend=y` (see full example below).

## Enabling debugging

The following example shows remote debugger configuration. The breakpoints
should work in any code except inline advice methods.

```bash
java -agentlib:jdwp="transport=dt_socket,server=y,suspend=y,address=5000" -javaagent:opentelemetry-javaagent-<version>.jar -jar app.jar
```

## Shadow Renaming

One additional thing that complicates debugging is that certain packages are renamed to avoid
conflict with whatever might already be on the classpath. This results in classes that don't match
what the IDE is expecting for debug breakpoints. You can disable the shadow renaming for local
builds by adding the following to `~/.gradle/gradle.properties` before building.

```properties
disableShadowRelocate=true
```

WARNING: disabling shadow renaming will make some of the tests fail. In some cases it can also make
tests pass when they really should be failing. Use with caution and be prepared for unexpected
behavior.

## Missing GraalVM hints

Enable the GraalVM tracing agent:

```
graalvmNative {

  ...

  agent {
    defaultMode.set("standard")
    enabled.set(true)
  }

}
```

Execute tests as native executables:

```
./gradlew nativeTest
```

The tracing data will be generated in the `build/native/agent-output` folder.
