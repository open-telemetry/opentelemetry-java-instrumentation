package datadog.trace.bootstrap.instrumentation.api;

import datadog.trace.context.TraceScope;

public interface AgentPropagation {

  TraceScope.Continuation capture();

  <C> void inject(AgentSpan span, C carrier, Setter<C> setter);

  interface Setter<C> {
    void set(C carrier, String key, String value);
  }

  <C> AgentSpan.Context extract(C carrier, Getter<C> getter);

  interface Getter<C> {
    Iterable<String> keys(C carrier);

    String get(C carrier, String key);
  }
}
