package datadog.trace.agent.tooling;

import datadog.opentracing.DDTracer;
import datadog.trace.api.Config;
import datadog.trace.instrumentation.api.AgentTracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register a global tracer if no global tracer is already registered. */
  public static synchronized void installGlobalTracer() {
    if (Config.get().isTraceEnabled()) {
      final DDTracer tracer = new DDTracer();
      try {
        datadog.trace.api.GlobalTracer.registerIfAbsent(tracer);
        AgentTracer.registerIfAbsent(new OpenTracing32(tracer));
      } catch (final RuntimeException re) {
        log.warn("Failed to register tracer '" + tracer + "'", re);
      }
    } else {
      log.debug("Tracing is disabled.");
    }
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
