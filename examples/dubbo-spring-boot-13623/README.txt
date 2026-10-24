Issue #13623: executable-JAR reproducer (not a production fix)
https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13623

This standalone Maven application exercises Dubbo 2.7.23 through the actual
Spring Boot JarLauncher and nested-JAR LaunchedURLClassLoader. Only the packaging
plugin/loader changes between builds: 1.3.8.RELEASE versus 1.4.7.RELEASE. No Spring
application context, external registry, collector, or agent build is required.
The consumer disables injvm, so the request crosses a real local TCP connection.

Run in an isolated development environment only. These are historical dependency
versions, not deployment recommendations. Agent 2.14.0 has a known RMI
deserialization vulnerability (fixed in 2.26.1); all commands disable RMI
instrumentation. QoS is disabled. The service binds an ephemeral loopback port.
The qualified hostname "localhost." avoids Dubbo 2.7's replacement of literal
localhost/127.* with a wildcard bind; the application checks its resolution.

Run the matrix
--------------
Requirements: JDK 8, Maven, curl, Bash, GNU timeout, and Maven Central access.
Run without other OTEL_*, JAVA_TOOL_OPTIONS, or Dubbo configuration overrides.
Set REPRO to the absolute path in your checkout and JAVA_HOME to your JDK 8.
On the cloud runner used for this reproduction:

  export JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
  export REPRO=/home/runner/work/opentelemetry-java-instrumentation/opentelemetry-java-instrumentation/examples/dubbo-spring-boot-13623
  java -version
  mvn -version

Build and download once (stop if any command fails):

  mkdir -p "$REPRO/target/agents"
  for boot in 1.3.8.RELEASE 1.4.7.RELEASE; do
    mvn -B -f "$REPRO/pom.xml" package -Dspring-boot.version="$boot" || exit 1
  done
  for agent in 2.33.0 2.14.0; do
    curl -fL "https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/$agent/opentelemetry-javaagent-$agent.jar" \
      -o "$REPRO/target/agents/agent-$agent.jar" || exit 1
  done

Run all four combinations. Preserve nonzero exits: exit 2 can be the SPI symptom;
it must not stop the control runs. Full stdout/stderr is retained under target.

  for boot in 1.3.8.RELEASE 1.4.7.RELEASE; do
    for agent in 2.33.0 2.14.0; do
      log="$REPRO/target/boot-$boot-agent-$agent.log"
      status=0
      timeout 90s "$JAVA_HOME/bin/java" \
        -javaagent:"$REPRO/target/agents/agent-$agent.jar" \
        -Dotel.service.name=issue-13623 \
        -Dotel.traces.exporter=logging \
        -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
        -Dotel.instrumentation.rmi.enabled=false \
        -jar "$REPRO/target/reproducer-$boot.jar" > "$log" 2>&1 || status=$?
      echo "Boot=$boot agent=$agent exit=$status log=$log"
      grep -E 'RPC_|RESOURCE_COUNT|SPI_ENTRY|DISCOVERED_FILTER|HELPER_LOAD_|FILTERS client=|RESULT|LoggingSpanExporter' "$log"
    done
  done

Interpretation
--------------
Diagnostics run AFTER the natural RPC attempt, not before SPI initialization.
APPLICATION_LOADER/TCCL/EXTENSION_LOADER/FILTER_LOADER identify the actual loaders.
RESOURCE_ONE/RESOURCE_ALL/RESOURCE_COUNT report getResource/getResources and
SPI_ENTRY prints OpenTelemetry entries from the resources. DISCOVERED_FILTER
means Dubbo discovered and instantiated that extension; HELPER_LOAD_OK alone
only proves helper-class visibility, not SPI discovery or filter activation.

Exit 0 means RPC and both filters succeeded, 1 means RPC/startup/cleanup failed,
2 means RPC succeeded but at least one filter was not discovered/instantiated,
and 3 means diagnostics failed. A JVM/agent error may exit before RESULT; timeout
exits 124. Neither an unrelated error nor exit 2 alone establishes reproduction.
Inspect the full logs. Span export is verified separately: look for one CLIENT
and one SERVER LoggingSpanExporter line for App$EchoService/echo, scope
io.opentelemetry.apache-dubbo-2.7, rpc.system=apache_dubbo, with the same trace ID.
The logging exporter needs no collector; the control validates the exporter path.

Observed on 2026-10-24
----------------------
Linux amd64, Temurin 1.8.0_504-b01, Maven 3.10.0, Dubbo 2.7.23:

Boot plugin       Agent    RPC       Client/server SPI   Dubbo spans   Exit
1.3.8.RELEASE     2.33.0   success   both absent         none          2
1.3.8.RELEASE     2.14.0   success   both absent         none          2
1.4.7.RELEASE     2.33.0   success   both present        CLIENT+SERVER 0
1.4.7.RELEASE     2.14.0   success   both present        CLIENT+SERVER 0

The issue reproduces with the latest release tested (2.33.0), not just 2.14.0.
Both helper classes loaded in all four runs. Boot 1.3.8 returned null/zero
resources for META-INF/services/org.apache.dubbo.rpc.Filter; its ordinary Dubbo
internal SPI resource was readable. Boot 1.4.7 returned the injected
x-internal-jar resource containing both OpenTelemetry filters. Each successful
control exported the expected two spans sharing a trace ID.

Diagnosis
---------
ResourceInjectionInstrumentation already handles overridden getResource(s).
However, AdditionalLibraryIgnoredTypesConfigurer ignores org.springframework.boot.
without allowing its loader. In the default 2.33.0 debug run, LaunchedURLClassLoader
was not transformed, while Dubbo helpers were injected into that exact loader.
The packaged 1.3.8 loader overrides getResource/getResources, consults its root
ancestor rather than the immediate parent, then searches its own URLs. This
bypasses the instrumented inherited methods. The 1.4.7 loader no longer overrides
these methods, so inherited resource handling can find the injected resource.
HelperResources registrations are keyed by the exact loader: skipped parent
delegation alone is not a sufficient explanation.

As a separate diagnostic experiment, repeat ONLY the 1.3.8/2.33.0 run above with:

  -Dotel.javaagent.debug=true
  -Dotel.javaagent.testing.additional-library-ignores.enabled=false

That run transformed LaunchedURLClassLoader, discovered both filters, completed
the RPC, exported CLIENT+SERVER spans, and exited 0. This is an internal testing
switch, not a supported user workaround. Keep it OUT of the reproduction matrix.
Use -Dotel.javaagent.debug=true alone to retain normal behavior with detailed
injection/transformation diagnostics. No agent implementation is changed here.
