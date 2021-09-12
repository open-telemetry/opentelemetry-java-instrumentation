### Understanding the javaagent components

The javaagent jar can logically be divided into 3 parts:

* Modules that live in the system class loader
* Modules that live in the bootstrap class loader
* Modules that live in the agent class loader

### Modules that live in the system class loader

#### `javaagent` module

This module consists of single class
`io.opentelemetry.javaagent.OpenTelemetryAgent` which implements [Java
instrumentation
agent](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html).
This class is loaded during application startup by application classloader.
Its sole responsibility is to push agent's classes into JVM's bootstrap
classloader and immediately delegate to
`io.opentelemetry.javaagent.bootstrap.AgentInitializer` (now in the bootstrap class loader)
class from there.

### Modules that live in the bootstrap class loader

#### `javaagent-bootstrap` module

`io.opentelemetry.javaagent.bootstrap.AgentInitializer` and a few other classes that live in the bootstrap class
loader but are not used directly by auto-instrumentation

#### `instrumentation-api` and `javaagent-instrumentation-api` modules

These modules contain support classes for actual instrumentations to be loaded
later and separately. These classes should be available from all possible
classloaders in the running application. For this reason the `javaagent` module puts
all these classes into JVM's bootstrap classloader. For the same reason this
module should be as small as possible and have as few dependencies as
possible. Otherwise, there is a risk of accidentally exposing these classes to
the actual application.

`instrumentation-api` contains classes that are needed for both library and auto-instrumentation,
while `javaagent-instrumentation-api` contains classes that are only needed for auto-instrumentation.

### Modules that live in the agent class loader

#### `javaagent-tooling`, `javaagent-extension-api` modules and `instrumentation` submodules

Contains everything necessary to make instrumentation machinery work,
including integration with [ByteBuddy](https://bytebuddy.net/) and actual
library-specific instrumentations. As these classes depend on many classes
from different libraries, it is paramount to hide all these classes from the
host application. This is achieved in the following way:

- When `javaagent` module builds the final agent, it moves all classes from
`instrumentation` submodules, `javaagent-tooling` and `javaagent-extension-api` modules
into a separate folder inside final jar file, called`inst`.
In addition, the extension of all class files is changed from `class` to `classdata`.
This ensures that general classloaders cannot find nor load these classes.
- When `io.opentelemetry.javaagent.bootstrap.AgentInitializer` is invoked, it creates an
instance of `io.opentelemetry.javaagent.bootstrap.AgentClassLoader`, loads an
`io.opentelemetry.javaagent.tooling.AgentInstaller` from that `AgentClassLoader`
and then passes control on to the `AgentInstaller` (now in the
`AgentClassLoader`). The `AgentInstaller` then installs all of the
instrumentations with the help of ByteBuddy. Instead of using agent classloader all agent classes
could be shaded and used from the bootstrap classloader. However, this opens de-serialization
security vulnerability and in addition to that the shaded classes are harder to debug.

The complicated process above ensures that the majority of
auto-instrumentation agent's classes are totally isolated from application
classes, and an instrumented class from arbitrary classloader in JVM can
still access helper classes from bootstrap classloader.

#### Agent jar structure

If you now look inside
`javaagent/build/libs/opentelemetry-javaagent-<version>.jar`, you will see the
following "clusters" of classes:

Available in the system class loader:

- `io/opentelemetry/javaagent/bootstrap/AgentBootstrap` - the one class from `javaagent`
module

Available in the bootstrap class loader:

- `io/opentelemetry/javaagent/bootstrap/` - contains the `javaagent-bootstrap` module
- `io/opentelemetry/javaagent/instrumentation/api/` - contains the `javaagent-instrumentation-api` module
- `io/opentelemetry/javaagent/shaded/instrumentation/api/` - contains the `instrumentation-api` module,
 shaded during creation of `javaagent` jar file by Shadow Gradle plugin
- `io/opentelemetry/javaagent/shaded/io/` - contains the OpenTelemetry API and its dependency gRPC
Context, both shaded during creation of `javaagent` jar file by Shadow Gradle plugin
- `io/opentelemetry/javaagent/slf4j/` - contains SLF4J and its simple logger implementation, shaded
during creation of `javaagent` jar file by Shadow Gradle plugin

Available in the agent class loader:
- `inst/` - contains `javaagent-tooling` and `javaagent-extension-api` modules and
  `instrumentation` submodules, loaded and isolated inside `AgentClassLoader`.
  Includes the OpenTelemetry SDK.

![Agent initialization sequence](initialization-sequence.svg)
[Image source](https://docs.google.com/drawings/d/1GHAcJ8AOaf_v2Ip82cQD9dN0mtvSk2C1B11KfwV2U8o)
![Agent classloader state](classloader-state.svg)
[Image source](https://docs.google.com/drawings/d/1x_eiGRodZ715ai6gDMTkyPYU4_wQnEkS4LQKSasEJAk)
