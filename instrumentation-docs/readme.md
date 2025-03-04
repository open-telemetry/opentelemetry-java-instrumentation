# Doc Generator

Runs analysis on instrumentation modules in order to generate documentation.


## Instrumentation Hierarchy

An "InstrumentationEntity" represents a module that that targets specific code in a framework/library/technology.
Each instrumentation uses muzzle to determine which versions of the target code it supports.

Using these structures as examples:

```
├── instrumentation
│   ├── clickhouse-client-05
│   ├── jaxrs
│   │   ├── jaxrs-1.0
│   │   ├── jaxrs-2.0
│   ├── spring
│   │   ├── spring-cloud-gateway
│   │   │   ├── spring-cloud-gateway-2.0
│   │   │   ├── spring-cloud-gateway-2.2
│   │   │   └── spring-cloud-gateway-common
```

* Name
  * Ex: `clickhouse-client-05`, `jaxrs-1.0`, `spring-cloud-gateway-2.0`
* Namespace - direct parent. if none, use name and strip version
  * `clickhouse-client`, `jaxrs`, `spring-cloud-gateway`
* Group - top most parent
  * `clickhouse-client`, `jaxrs`, `spring`

This information is also referenced in `InstrumentationModule` code for each module:

```java
public class SpringWebInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SpringWebInstrumentationModule() {
    super("spring-web", "spring-web-3.1");
  }
```

## Instrumentation meta-data:

* name
  * Identifier for instrumentation module, used to enable/disable
  * Configured in `InstrumentationModule` code for each module
* versions
  * List of supported versions by the module
* type
  * List of instrumentation types, options of either `library` or `javaagent`

## Methodology

### Versions targeted

Javaagent versions are determined by the `muzzle` plugin, so we can attempt to parse the gradle files

Library versions are determined by the library versions used in the gradle files.


### TODO

- [ ] Is there a better way to summarize/present the `target_version` information?
- [ ] Fix target_version when a variable is used, (example: zio - `dev.zio:zio_2.12:[$zioVersion,)`)
