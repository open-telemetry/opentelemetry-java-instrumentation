### Understanding the javaagent components

OpenTelemetry Auto Instrumentation java agent's jar can logically be divided
into 3 parts.

#### `opentelemetry-javaagent` module

This module consists of single class
`io.opentelemetry.auto.bootstrap.AgentBootstrap` which implements [Java
instrumentation
agent](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html).
This class is loaded during application startup by application classloader.
Its sole responsibility is to push agent's classes into JVM's bootstrap
classloader and immediately delegate to
`io.opentelemetry.auto.bootstrap.Agent` (now in the bootstrap class loader)
class from there.

#### `agent-bootstrap` module

This module contains support classes for actual instrumentations to be loaded
later and separately. These classes should be available from all possible
classloaders in the running application. For this reason `java-agent` puts
all these classes into JVM's bootstrap classloader. For the same reason this
module should be as small as possible and have as few dependencies as
possible. Otherwise, there is a risk of accidentally exposing this classes to
the actual application.

#### `agent-tooling` module and `instrumentation` submodules

Contains everything necessary to make instrumentation machinery work,
including integration with [ByteBuddy](https://bytebuddy.net/) and actual
library-specific instrumentations. As these classes depend on many classes
from different libraries, it is paramount to hide all these classes from the
host application. This is achieved in the following way:

- When `java-agent` module builds the final agent, it moves all classes from
`instrumentation` submodules and `agent-tooling` module into a separate
folder inside final jar file, called`inst`.
In addition, the extension of all class files is changed from `class` to `classdata`.
This ensures that general classloaders cannot find nor load these classes.
- When `io.opentelemetry.auto.bootstrap.Agent` starts up, it creates an
instance of `io.opentelemetry.auto.bootstrap.AgentClassLoader`, loads an
`io.opentelemetry.auto.tooling.AgentInstaller` from that `AgentClassLoader`
and then passes control on to the `AgentInstaller` (now in the
`AgentClassLoader`). The `AgentInstaller` then installs all of the
instrumentations with the help of ByteBuddy.

The complicated process above ensures that the majority of
auto-instrumentation agent's classes are totally isolated from application
classes, and an instrumented class from arbitrary classloader in JVM can
still access helper classes from bootstrap classloader.

#### Agent jar structure

If you now look inside
`opentelemetry-javaagent/build/libs/opentelemetry-javaagent-<version>-all.jar`, you will see the
following "clusters" of classes:

- `inst/` - contains `agent-tooling` module and `instrumentation` submodules, loaded and isolated inside
`AgentClassLoader`. Including OpenTelemetry SDK (and the built-in exporters when using the `-all` artifact).
- `io/opentelemetry/auto/bootstrap/` - contains `agent-bootstrap` module and available in bootstrap classloader.
- `io/opentelemetry/auto/shaded/` - contains OpenTelemetry API and its dependencies.
Shaded during creation of `javaagent` jar file by Shadow Gradle plugin.

