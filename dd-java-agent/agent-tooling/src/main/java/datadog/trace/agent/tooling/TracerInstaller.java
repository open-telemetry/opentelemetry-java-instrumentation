package datadog.trace.agent.tooling;

import datadog.opentracing.DDTracer;
import datadog.trace.api.Config;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register a global tracer if no global tracer is already registered. */
  public static synchronized void installGlobalTracer() {
    if (Config.get().isTraceEnabled()) {
      if (!io.opentracing.util.GlobalTracer.isRegistered()) {
        final DDTracer tracer = new DDTracer();
        try {
          io.opentracing.util.GlobalTracer.register(tracer);
          datadog.trace.api.GlobalTracer.registerIfAbsent(tracer);
        } catch (final RuntimeException re) {
          log.warn("Failed to register tracer '" + tracer + "'", re);
        }
      } else {
        log.debug("GlobalTracer already registered.");
      }
    } else {
      log.debug("Tracing is disabled, not installing GlobalTracer.");
    }
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        io.opentracing.util.GlobalTracer.class.getName()
            + " loaded on "
            + io.opentracing.util.GlobalTracer.class.getClassLoader());
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
