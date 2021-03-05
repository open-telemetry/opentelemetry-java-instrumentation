/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context;

import static io.opentelemetry.javaagent.instrumentation.rmi.client.RmiClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ContextPayload wraps context information shared between client and server. */
public class ContextPayload {

  private static final Logger log = LoggerFactory.getLogger(ContextPayload.class);

  private final Map<String, String> context;
  public static final ExtractAdapter GETTER = new ExtractAdapter();
  public static final InjectAdapter SETTER = new InjectAdapter();

  public ContextPayload() {
    context = new HashMap<>();
  }

  public ContextPayload(Map<String, String> context) {
    this.context = context;
  }

  public static ContextPayload from(Context context) {
    ContextPayload payload = new ContextPayload();
    tracer().inject(context, payload, SETTER);
    return payload;
  }

  public static ContextPayload read(ObjectInput oi) throws IOException {
    try {
      Object object = oi.readObject();
      if (object instanceof Map) {
        return new ContextPayload((Map<String, String>) object);
      }
    } catch (ClassCastException | ClassNotFoundException ex) {
      log.debug("Error reading object", ex);
    }

    return null;
  }

  public Map<String, String> getSpanContext() {
    return context;
  }

  public void write(ObjectOutput out) throws IOException {
    out.writeObject(context);
  }

  public static class ExtractAdapter implements TextMapGetter<ContextPayload> {
    @Override
    public Iterable<String> keys(ContextPayload contextPayload) {
      return contextPayload.getSpanContext().keySet();
    }

    @Override
    public String get(ContextPayload carrier, String key) {
      return carrier.getSpanContext().get(key);
    }
  }

  public static class InjectAdapter implements TextMapSetter<ContextPayload> {
    @Override
    public void set(ContextPayload carrier, String key, String value) {
      carrier.getSpanContext().put(key, value);
    }
  }
}
