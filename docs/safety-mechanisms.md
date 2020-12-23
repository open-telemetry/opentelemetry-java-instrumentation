# OpenTelemetry Java Agent Safety Mechanisms

This document outlines the safety mechanisms we have in place to have confidence
that the Java agent can be attached to a user's application with a very low chance of
affecting it negatively, for example introducing crashes.

## Instrumentation tests

All instrumentation are written with instrumentation tests - these can be considered the unit tests
of this project.

Instrumentation tests are run using a fully shaded `-javaagent` in order to perform the same bytecode
instrumentation as when the agent is run against a normal app.
By then exercising the instrumented library in a way a user would, for example by issuing requests
from an HTTP client, we can assert on the spans that should be generated, including their semantic
attributes. A problem in the instrumentation will generally cause spans to be reported incorrectly
or not reported at all, and we can find these situations with the instrumentation tests.

## Latest dep tests

Instrumentation tests are generally run against the lowest version of a library that we support
to ensure a baseline against users with old dependency versions. Due to the nature of the agent
and locations where we instrument private APIs, the agent may fail on a newly released version
of the library. We run instrumentation tests additionally against the latest version of the
library, as fetched from Maven, as part of a nightly build. If a new version of a library will
not work with the agent, we find out through this build and can address it by the next release
of the agent.

## Muzzle compile time checks

Muzzle is the tool we use to ensure we do not apply agent instrumentation if it would break the
user's app. Details on its implementation can be found [here](./contributing/muzzle.md).

Continuous build runs a muzzle compile time check for every library. This check will select random
versions of the library available in Maven and check if our agent will cleanly apply to it. The
check collects all references that the agent code makes, e.g., classes that are used and methods that
are called, and verifies the references exist in that version of the library. This is important
because if we apply the agent with missing references, it will generally cause crashes in the user's
app such as `NoSuchMethodError`. We cannot check every single version of every library in every build, it
would be too slow and wasteful of contributed resources. But by selecting random versions every
build, over time we can be confident that we know the agent can be used on all versions of a library
without causing linkage errors due to missing references.

## Muzzle runtime checks

The set of references from the agent used at Muzzle during compile time is also stored in the agent's
code itself. Similar to the compile time check, we also do a validation of the references available
in the user's app vs what is referenced by the agent instrumentation. If the references do not match
up, we will not load the instrumentation at runtime, preventing applying instrumentation that could
potentially cause linkage errors.

## Classloader separation

See more detail about the classloader separation [here](./contributing/javaagent-jar-components.md).

The Java agent makes sure to include as little code as possible in the user app's classloader, and
all code that is included is either unique to the agent itself or shaded in the agent build. This is
because if the agent included classes that are also used by the user's app and there was a version
mismatch, it could cause linkage crashes.

Instead of executing code in the app's classloader, the agent has its own agent classloader where
instrumentation is loaded and exporters and the SDK is configured. Only when applying an
instrumentation (which will have passed Muzzle runtime checks) do we inject any additional classes
that are needed by the instrumentation into the user's classloader. These classes are always either
unique to the agent or shaded versions of public libraries such as our library instrumentation
modules and cannot cause version conflicts.

To ensure agent classes are not automatically loaded into the user's classloader, possibly by an
eager loading application server, they are hidden in the agent JAR as standard, non-Java files.
All packages are moved into a subdirectory `inst` and all classes are renamed from `.class` to
`.classdata`, ensuring applications with any sort of automatic classpath scanning will not find
agent classes. The agent classloader understands this convention and unobfuscates when loading
classes.

## Smoke tests

We run docker-based smoke tests which have simple instrumented apps running under various JVMs
and application servers. In particular, application servers sometimes have fragile behavior using
internal details of the JVM which an agent can cause problems with. Smoke tests ensure compatibility
with a wide variety of application servers.
