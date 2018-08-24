package datadog.trace.agent.tooling;

import datadog.opentracing.DDTracer;
import datadog.trace.context.TracerBridge;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register a global tracer if no global tracer is already registered. */
  public static synchronized void installGlobalTracer() {
    if (!GlobalTracer.isRegistered()) {
      final DDTracer tracer = new DDTracer();
      try {
        GlobalTracer.register(tracer);
        TracerBridge.registerIfAbsent(tracer);
      } catch (final RuntimeException re) {
        log.warn("Failed to register tracer '" + tracer + "'", re);
      }
    } else {
      log.debug("GlobalTracer already registered.");
    }
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(GlobalTracer.class.getName() + " loaded on " + GlobalTracer.class.getClassLoader());
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
