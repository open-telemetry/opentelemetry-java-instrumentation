package com.datadoghq.agent;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;

/**
 * This manager is loaded at pre-main. It loads all the scripts contained in all the 'oatrules.btm'
 * resource files.
 */
@Slf4j
public class InstrumentationRulesManager {
  private static final Object SYNC = new Object();

  public InstrumentationRulesManager(
      final TracingAgentConfig config, final AgentRulesManager agentRulesManager) {}

  void initTracer() {
    synchronized (SYNC) {
      if (!GlobalTracer.isRegistered()) {
        // Try to obtain a tracer using the TracerResolver
        final Tracer resolved = TracerResolver.resolveTracer();
        if (resolved != null) {
          try {
            GlobalTracer.register(resolved);
          } catch (final RuntimeException re) {
            log.warn("Failed to register tracer '" + resolved + "'", re);
          }
        } else {
          log.warn("Failed to resolve dd tracer");
        }
      }
    }
  }
}
