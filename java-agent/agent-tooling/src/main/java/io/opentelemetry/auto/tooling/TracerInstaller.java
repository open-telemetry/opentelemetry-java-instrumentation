package io.opentelemetry.auto.tooling;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.Config;
import io.opentelemetry.auto.instrumentation.api.AgentTracer;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register agent tracer if no agent tracer is already registered. */
  public static synchronized void installAgentTracer() {
    if (Config.get().isTraceEnabled()) {
      final Tracer tracer = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");
      try {
        AgentTracer.registerIfAbsent(new AgentTracerImpl(tracer));
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
