package datadog.trace.instrumentation.rmi.server;

import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.agent.decorator.ServerDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.instrumentation.api.AgentSpan;

public class RmiServerDecorator extends ServerDecorator {
  public static final RmiServerDecorator DECORATE = new RmiServerDecorator();

  public AgentSpan startSpanWithContext(
      final ContextStore<Thread, AgentSpan.Context> contextStore) {
    if (activeSpan() != null) {
      return startSpan("rmi.request");
    }

    final AgentSpan.Context context = contextStore.get(Thread.currentThread());

    if (context == null) {
      return startSpan("rmi.request");
    } else {
      return startSpan("rmi.request", context);
    }
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rmi"};
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.RPC;
  }

  @Override
  protected String component() {
    return "rmi-server";
  }
}
