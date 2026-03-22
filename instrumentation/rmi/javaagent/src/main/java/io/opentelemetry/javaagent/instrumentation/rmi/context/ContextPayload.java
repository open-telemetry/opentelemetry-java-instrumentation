/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** ContextPayload wraps context information shared between client and server. */
public class ContextPayload {

  private static final Logger logger = Logger.getLogger(ContextPayload.class.getName());
  private static final int MAX_CONTEXT_ENTRIES = 1000;

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

  @Nullable
  public static ContextPayload read(ObjectInput oi) throws IOException {
    int size = oi.readInt();
    if (size > MAX_CONTEXT_ENTRIES) {
      logger.log(
          FINE,
          "RMI context propagation payload size {0} exceeds maximum allowed of {1}, skipping context propagation.",
          new Object[] {size, MAX_CONTEXT_ENTRIES});
      return null;
    }
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < size; i++) {
      String key = oi.readUTF();
      String value = oi.readUTF();
      map.put(key, value);
    }
    return new ContextPayload(map);
  }

  public void write(ObjectOutput out) throws IOException {
    int size = context.size();
    if (size > MAX_CONTEXT_ENTRIES) {
      logger.log(
          FINE,
          "RMI context propagation payload size {0} exceeds maximum allowed of {1}, skipping context propagation.",
          new Object[] {size, MAX_CONTEXT_ENTRIES});
      out.writeInt(0);
      return;
    }
    out.writeInt(size);
    for (Map.Entry<String, String> entry : context.entrySet()) {
      out.writeUTF(entry.getKey());
      out.writeUTF(entry.getValue());
    }
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
