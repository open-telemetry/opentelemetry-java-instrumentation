/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context;

import io.opentelemetry.api.GlobalOpenTelemetry;
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

  private static final Logger logger = LoggerFactory.getLogger(ContextPayload.class);

  private final Map<String, String> context;

  public ContextPayload() {
    this(new HashMap<>());
  }

  public ContextPayload(Map<String, String> context) {
    this.context = context;
  }

  public static ContextPayload from(Context context) {
    ContextPayload payload = new ContextPayload();
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(context, payload, ContextPayloadSetter.INSTANCE);
    return payload;
  }

  public static ContextPayload read(ObjectInput oi) throws IOException {
    try {
      Object object = oi.readObject();
      if (object instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) object;
        return new ContextPayload(map);
      }
    } catch (ClassCastException | ClassNotFoundException ex) {
      logger.debug("Error reading object", ex);
    }

    return null;
  }

  public void write(ObjectOutput out) throws IOException {
    out.writeObject(context);
  }

  public Context extract() {
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.root(), this, ContextPayloadGetter.INSTANCE);
  }

  private enum ContextPayloadGetter implements TextMapGetter<ContextPayload> {
    INSTANCE;

    @Override
    public Iterable<String> keys(ContextPayload contextPayload) {
      return contextPayload.context.keySet();
    }

    @Override
    public String get(ContextPayload carrier, String key) {
      return carrier.context.get(key);
    }
  }

  private enum ContextPayloadSetter implements TextMapSetter<ContextPayload> {
    INSTANCE;

    @Override
    public void set(ContextPayload carrier, String key, String value) {
      carrier.context.put(key, value);
    }
  }
}
