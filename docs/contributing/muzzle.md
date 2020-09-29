# Muzzle

Muzzle is a feature of the Java agent that ensures API compatibility
between libraries/symbols on the application classpath and APIs of instrumented
3rd party libraries used by the Agent. In other words the Muzzle ensures
that the API symbols used by the Agent are compatible with API symbols
on the application classpath. The Muzzle will prevent loading an instrumentation
if the APIs do not match.

## How does it work

At build time, for each instrumentation the Muzzle ByteBuddy plugin collects symbols referring to both internal
and 3rd party APIs used by the currently processed instrumentation. The reference collection process starts
from advice classes - values of the map returned by the `Instrumenter.Default#transformers()` method.

All those references are then used to create a `ReferenceMatcher` instance.
The matcher is stored in the instrumentation class in method `ReferenceMatcher getInstrumentationMuzzle()`.

At runtime the Muzzle checks API compatibility between symbols used by the Agent
and symbols in the application class loader. If the symbols do not match the instrumentation is not loaded.
Because the muzzle matcher is expensive, it is only performed after a match has been made by the 
`SomeInstrumentation.classLoaderMatcher()` and `SomeInstrumentation.typeMatcher()` matchers. 

## Muzzle gradle plugin

The `printReferences` task prints all API references in a given module.

```bash
./gradlew :instrumentation:google-http-client-1.19:printReferences
```

The `muzzle` task downloads 3rd party libraries from maven central and checks API compatibility.
If a new incompatible version is published it fails the build.

```bash
./gradlew :instrumentation:google-http-client-1.19:muzzle 
```

## Muzzle location

* `buildSrc` - Muzzle Gradle plugin
* `agent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/muzzle` - Muzzle ByteBuddy plugin