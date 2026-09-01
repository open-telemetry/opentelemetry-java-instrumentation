/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
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

  private static final Logger logger = Logger.getLogger(ContextMetadata.class.getName());

  private static final byte VERSION = 1;
  // messages share the artery frame with the metadata, don't let a large context eat into the
  // space that is available for the message itself
  private static final int MAX_BYTES = 4 * 1024;
  // the limits that the rmi and thrift context propagation use, modeled on tomcat's
  // maxHeaderCount and maxHttpHeaderSize; the size is counted in characters
  private static final int MAX_CONTEXT_ENTRIES = 100;
  private static final int MAX_CONTEXT_SIZE = 8 * 1024;

  private static final TextMapSetter<Map<String, String>> setter = ContextMetadata::put;
  private static final TextMapGetter<Map<String, String>> getter =
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

  private static void put(@Nullable Map<String, String> carrier, String key, String value) {
    if (carrier != null) {
      carrier.put(key, value);
    }
  }

  public static void write(Context context, ByteBuffer buffer) {
    Map<String, String> fields = new LinkedHashMap<>();
    GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, fields, setter);
    if (fields.isEmpty()) {
      return;
    }
    if (fields.size() > MAX_CONTEXT_ENTRIES) {
      logger.log(
          FINE,
          "Not propagating context, {0} entries exceeds the maximum of {1}",
          new Object[] {fields.size(), MAX_CONTEXT_ENTRIES});
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
      if (buffer.position() - startPosition > MAX_BYTES) {
        // rewind, pekko treats writing nothing as "this instrument has no metadata"
        buffer.position(startPosition);
      }
    } catch (BufferOverflowException ignored) {
      buffer.position(startPosition);
    }
  }

  /**
   * Reads the context back, null when there is none. The bytes were sent by another node, which
   * does not necessarily run this version or run the agent in good faith, so nothing about them is
   * trusted: input that goes over the limits or does not fit its own length fields is reported as
   * no context rather than thrown at pekko.
   */
  @Nullable
  public static Context read(ByteBuffer buffer) {
    if (buffer.get() != VERSION) {
      return null;
    }
    int count = buffer.getShort();
    if (count <= 0) {
      return null;
    }
    if (count > MAX_CONTEXT_ENTRIES) {
      logger.log(
          FINE,
          "Ignoring context metadata, {0} entries exceeds the maximum of {1}",
          new Object[] {count, MAX_CONTEXT_ENTRIES});
      return null;
    }
    int size = 0;
    Map<String, String> fields = new HashMap<>();
    for (int i = 0; i < count; i++) {
      String key = readString(buffer);
      String value = key == null ? null : readString(buffer);
      if (key == null || value == null) {
        return null;
      }
      size += key.length() + value.length();
      if (size > MAX_CONTEXT_SIZE) {
        logger.log(
            FINE,
            "Ignoring context metadata larger than the maximum of {0} characters",
            MAX_CONTEXT_SIZE);
        return null;
      }
      fields.put(key, value);
    }
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.root(), fields, getter);
  }

  private static void writeString(ByteBuffer buffer, String value) {
    byte[] bytes = value.getBytes(UTF_8);
    buffer.putShort((short) bytes.length);
    buffer.put(bytes);
  }

  /** Reads a length prefixed string, null when the length does not fit what remains. */
  @Nullable
  private static String readString(ByteBuffer buffer) {
    if (buffer.remaining() < 2) {
      return null;
    }
    int length = buffer.getShort();
    if (length < 0 || length > buffer.remaining()) {
      return null;
    }
    byte[] bytes = new byte[length];
    buffer.get(bytes);
    return new String(bytes, UTF_8);
  }

  private ContextMetadata() {}
}
