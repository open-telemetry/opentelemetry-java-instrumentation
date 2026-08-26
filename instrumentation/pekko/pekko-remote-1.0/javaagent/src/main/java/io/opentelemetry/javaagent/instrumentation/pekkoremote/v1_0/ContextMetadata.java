/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Reads and writes the context that is attached to a remote message.
 *
 * <p>The metadata is self describing so that reading it does not depend on how pekko frames the
 * data that a {@code RemoteInstrument} writes:
 *
 * <pre>
 *   byte  version
 *   short field count
 *   for each field:
 *     short key length, key bytes (utf-8)
 *     short value length, value bytes (utf-8)
 * </pre>
 *
 * <p>A reader that does not know the version skips the metadata, pekko repositions the buffer to
 * the end of the section after the instrument has run.
 */
public final class ContextMetadata {

  private static final byte VERSION = 1;
  // messages share the artery frame with the metadata, don't let a large context eat into the
  // space that is available for the message itself
  private static final int MAX_SIZE = 4 * 1024;

  private static final TextMapSetter<Map<String, String>> SETTER = Map::put;
  private static final TextMapGetter<Map<String, String>> GETTER =
      new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        @Nullable
        public String get(@Nullable Map<String, String> carrier, String key) {
          return carrier == null ? null : carrier.get(key);
        }
      };

  public static void write(Context context, ByteBuffer buffer) {
    Map<String, String> fields = new LinkedHashMap<>();
    GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, fields, SETTER);
    if (fields.isEmpty()) {
      return;
    }

    int startPosition = buffer.position();
    try {
      buffer.put(VERSION);
      buffer.putShort((short) fields.size());
      for (Map.Entry<String, String> field : fields.entrySet()) {
        writeString(buffer, field.getKey());
        writeString(buffer, field.getValue());
      }
      if (buffer.position() - startPosition > MAX_SIZE) {
        // rewind, pekko treats writing nothing as "this instrument has no metadata"
        buffer.position(startPosition);
      }
    } catch (BufferOverflowException exception) {
      buffer.position(startPosition);
    }
  }

  @Nullable
  public static Context read(ByteBuffer buffer) {
    if (buffer.get() != VERSION) {
      return null;
    }
    int count = buffer.getShort();
    if (count <= 0) {
      return null;
    }
    Map<String, String> fields = new HashMap<>();
    for (int i = 0; i < count; i++) {
      String key = readString(buffer);
      fields.put(key, readString(buffer));
    }
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.root(), fields, GETTER);
  }

  private static void writeString(ByteBuffer buffer, String value) {
    byte[] bytes = value.getBytes(UTF_8);
    buffer.putShort((short) bytes.length);
    buffer.put(bytes);
  }

  private static String readString(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.getShort()];
    buffer.get(bytes);
    return new String(bytes, UTF_8);
  }

  private ContextMetadata() {}
}
