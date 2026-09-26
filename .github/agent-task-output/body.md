Move the Reactor 3.4 ContextView instrumentation into the Reactor 3.1 javaagent module, removing the separate Gradle project.

Keep the existing instrumentation names and enablement behavior. Baseline Reactor hooks and context propagation remain active on Reactor 3.4+, with separate Muzzle ranges for the ContextView bridge. Reactor Kafka and Reactor Netty remain separate modules.

Part of #20189.
