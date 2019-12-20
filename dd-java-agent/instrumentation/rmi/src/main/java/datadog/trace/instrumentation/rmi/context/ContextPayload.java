package datadog.trace.instrumentation.rmi.context;

import static datadog.trace.instrumentation.api.AgentTracer.propagate;

import datadog.trace.instrumentation.api.AgentPropagation;
import datadog.trace.instrumentation.api.AgentSpan;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** ContextPayload wraps context information shared between client and server */
@Slf4j
public class ContextPayload {
  @Getter private final Map<String, String> context;
  public static final ExtractAdapter GETTER = new ExtractAdapter();
  public static final InjectAdapter SETTER = new InjectAdapter();

  public ContextPayload() {
    context = new HashMap<>();
  }

  public ContextPayload(final Map<String, String> context) {
    this.context = context;
  }

  public static ContextPayload from(final AgentSpan span) {
    final ContextPayload payload = new ContextPayload();
    propagate().inject(span, payload, SETTER);
    return payload;
  }

  public static ContextPayload read(final ObjectInput oi) throws IOException {
    try {
      final Object object = oi.readObject();
      if (object instanceof Map) {
        return new ContextPayload((Map<String, String>) object);
      }
    } catch (final ClassCastException | ClassNotFoundException ex) {
      log.debug("Error reading object", ex);
    }

    return null;
  }

  public void write(final ObjectOutput out) throws IOException {
    out.writeObject(context);
  }

  public static class ExtractAdapter implements AgentPropagation.Getter<ContextPayload> {
    @Override
    public Iterable<String> keys(final ContextPayload carrier) {
      return carrier.getContext().keySet();
    }

    @Override
    public String get(final ContextPayload carrier, final String key) {
      return carrier.getContext().get(key);
    }
  }

  public static class InjectAdapter implements AgentPropagation.Setter<ContextPayload> {
    @Override
    public void set(final ContextPayload carrier, final String key, final String value) {
      carrier.getContext().put(key, value);
    }
  }
}
