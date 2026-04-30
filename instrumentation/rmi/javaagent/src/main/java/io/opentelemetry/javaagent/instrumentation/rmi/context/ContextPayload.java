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
  // using limits similar to Tomcat's maxHeaderCount and maxHttpHeaderSize
  // https://tomcat.apache.org/tomcat-9.0-doc/config/http.html
  private static final int MAX_CONTEXT_ENTRIES = 100;
  // Tomcat limits in bytes we use character count
  private static final int MAX_CONTEXT_SIZE = 8 * 1024;

  private final Map<String, String> context;

  private ContextPayload() {
    this(new HashMap<>());
  }

  private ContextPayload(Map<String, String> context) {
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
          "RMI context propagation entries count {0} exceeds maximum allowed of {1}, skipping context propagation.",
          new Object[] {size, MAX_CONTEXT_ENTRIES});
      return null;
    }
    int contextSize = 0;
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < size; i++) {
      String key = oi.readUTF();
      String value = oi.readUTF();
      contextSize += key.length() + value.length();
      if (contextSize > MAX_CONTEXT_SIZE) {
        logger.log(
            FINE,
            "RMI context propagation payload size exceeds maximum allowed of {0}, skipping context propagation.",
            new Object[] {MAX_CONTEXT_SIZE});
        return null;
      }
      map.put(key, value);
    }
    return new ContextPayload(map);
  }

  public void write(ObjectOutput out) throws IOException {
    int size = context.size();
    if (size > MAX_CONTEXT_ENTRIES) {
      logger.log(
          FINE,
          "RMI context propagation entries count {0} exceeds maximum allowed of {1}, skipping context propagation.",
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
    @Nullable
    public String get(@Nullable ContextPayload carrier, String key) {
      if (carrier == null) {
        return null;
      }
      return carrier.context.get(key);
    }
  }

  private enum ContextPayloadSetter implements TextMapSetter<ContextPayload> {
    INSTANCE;

    @Override
    public void set(@Nullable ContextPayload carrier, String key, String value) {
      if (carrier == null) {
        return;
      }
      carrier.context.put(key, value);
    }
  }
}
