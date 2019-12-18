package datadog.trace.agent.test.utils;

import datadog.opentracing.DDTracer;
import datadog.trace.agent.tooling.AgentTracerImpl;
import datadog.trace.instrumentation.api.AgentTracer;

public class GlobalTracerUtils {
  public static void registerOrReplaceGlobalTracer(final DDTracer tracer) {
    AgentTracer.registerIfAbsent(new AgentTracerImpl(tracer));
  }
}
