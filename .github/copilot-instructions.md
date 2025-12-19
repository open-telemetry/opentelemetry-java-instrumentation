# OpenTelemetry Java Instrumentation

## Testing

Tests use AssertJ for assertions and JUnit 5 as the testing framework

Test classes and methods should not be public

When registering tests in gradle configurations, if using `val testName by registering(Test::class) {`...
then you need to include `testClassesDirs` and `classpath` like so:

```
val testExperimental by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  ...
}
```

## General Java guidelines

* Always import classes when possible (i.e. don't use fully qualified class names in code).

## Gradle CLI

Never use the `--rerun-tasks` flag unless explicitly asked to use this option.

Gradle automatically detects changes and re-runs tasks automatically when needed. Using `--rerun-tasks`
is wasteful and slows down builds unnecessarily.

## Throwing exceptions

When writing instrumentation, you have to be really careful about throwing exceptions. For library
instrumentations it might be acceptable, but in javaagent code you shouldn't throw exceptions
(keep in mind that javaagent instrumentations sometimes use library instrumentations).

In javaagent instrumentations we try not to break applications. If there are changes in the instrumented
library that are not compatible with the instrumentation we disable the instrumentation instead of letting
it fail. This is handled by muzzle. In javaagent instrumentations you should not fail if the methods
that you need don't exist.

## Javaagent Instrumentation

### Java8BytecodeBridge

When to use `Java8BytecodeBridge.currentContext()` vs `Context.current()` ?

Using `Context.current()` is preferred. `Java8BytecodeBridge.currentContext()` is for using inside
advice. We need this method because advice code is inlined in the instrumented method as it is.
Since `Context.current()` is a static interface method it will cause a bytecode verification error
when it is inserted into a pre 8 class. `Java8BytecodeBridge.currentContext()` is a regular class
static method and can be used in any class version.
