# Muzzle

Muzzle is a feature of the Java agent that ensures API compatibility
between libraries/symbols on the application classpath and APIs of instrumented
3rd party libraries used by the Agent. In other words the Muzzle ensures
that the API symbols used by the Agent are compatible with API symbols
on the application classpath. The Muzzle can prevent loading an instrumentation
if the APIs do not match.

## How does it work

At build time the Muzzle gradle plugin generates matcher for 3rd party APIs used by the agent.
The matcher is created in the instrumentation class in `ReferenceMatcher getInstrumentationMuzzle()`.

At runtime the Muzzle checks API compatibility between symbols used by the Agent
and symbols at the application classpath. Before inspecting the classpath
the Muzzle plugin checks symbols defined in `SomeInstrumentation.typeMatcher()`
as a performance optimization. If the symbols do not match the instrumentation
is not loaded.

## Muzzle gradle plugin

The `printReferences` task prints 3rd party API references in a given module.

```bash
./gradlew :instrumentation:google-http-client-1.19:printReferences
```