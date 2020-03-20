package datadog.trace.instrumentation.netty38;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
import lombok.Data;

@Data
public class ChannelTraceContext {
  public static class Factory implements ContextStore.Factory<ChannelTraceContext> {
    public static final Factory INSTANCE = new Factory();

    @Override
    public ChannelTraceContext create() {
      return new ChannelTraceContext();
    }
  }

  TraceScope.Continuation connectionContinuation;
  AgentSpan serverSpan;
  AgentSpan clientSpan;
  AgentSpan clientParentSpan;

  public boolean compareAndSet(
      final TraceScope.Continuation compareTo, final TraceScope.Continuation setTo) {
    if (connectionContinuation == compareTo) {
      connectionContinuation = setTo;
      return true;
    } else {
      return false;
    }
  }

  public TraceScope.Continuation getConnectionContinuationAndRemove() {
    final TraceScope.Continuation current = connectionContinuation;
    connectionContinuation = null;
    return current;
  }
}
