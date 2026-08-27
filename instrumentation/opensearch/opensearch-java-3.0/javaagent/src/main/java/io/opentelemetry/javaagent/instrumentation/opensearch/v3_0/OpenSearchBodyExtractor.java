/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.logging.Level.FINE;

import com.fasterxml.jackson.core.JsonFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;

class OpenSearchBodyExtractor {

  private static final Logger logger = Logger.getLogger(OpenSearchBodyExtractor.class.getName());
  private static final int MAX_QUERY_BODY_BYTES = 32 * 1024;
  private static final String QUERY_SEPARATOR = ";";
  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  @Nullable
  public static String extractSanitized(JsonpMapper mapper, Object request) {
    try {
      if (request instanceof NdJsonpSerializable) {
        return serializeNdJsonSanitized(
            mapper, (NdJsonpSerializable) request, MAX_QUERY_BODY_BYTES);
      }

      return serializeSanitized(mapper, request, MAX_QUERY_BODY_BYTES);
    } catch (Exception e) {
      logger.log(FINE, "Failure extracting body", e);
      return null;
    }
  }

  @Nullable
  private static String serializeSanitized(JsonpMapper mapper, Object item, int maxBytes)
      throws IOException {
    BoundedStringWriter writer = new BoundedStringWriter(maxBytes);

    try {
      if (mapper instanceof JacksonJsonpMapper) {
        // Use Jackson-based sanitizing generator for JacksonJsonpMapper
        com.fasterxml.jackson.core.JsonGenerator jacksonGenerator =
            JSON_FACTORY.createGenerator(writer);
        com.fasterxml.jackson.core.JsonGenerator sanitizingGenerator =
            new SanitizingJacksonJsonGenerator(jacksonGenerator);
        try (JsonGenerator generator = new JacksonJsonpGenerator(sanitizingGenerator)) {
          mapper.serialize(item, generator);
        }
      } else {
        // Fallback for other mappers (may not work for all implementations)
        JsonGenerator rawGenerator = mapper.jsonProvider().createGenerator(writer);
        try (JsonGenerator generator = new SanitizingJsonGenerator(rawGenerator)) {
          mapper.serialize(item, generator);
        }
      }
    } catch (RuntimeException e) {
      if (!causedByQueryBodyLimit(e)) {
        throw e;
      }
    }

    String result = writer.toString().trim();
    return result.isEmpty() ? null : result;
  }

  @Nullable
  private static String serializeNdJsonSanitized(
      JsonpMapper mapper, NdJsonpSerializable value, int maxBytes) throws IOException {
    StringBuilder result = new StringBuilder(Math.min(maxBytes, 1024));
    Iterator<?> values = value._serializables();
    boolean first = true;
    int resultBytes = 0;

    while (values.hasNext() && resultBytes < maxBytes) {
      Object item = values.next();
      String itemStr;
      int remaining = maxBytes - resultBytes;

      if (item instanceof NdJsonpSerializable && item != value) {
        // Recursively handle nested NdJsonpSerializable
        itemStr = serializeNdJsonSanitized(mapper, (NdJsonpSerializable) item, remaining);
      } else {
        itemStr = serializeSanitized(mapper, item, remaining);
      }

      if (itemStr != null && !itemStr.isEmpty()) {
        if (!first) {
          resultBytes = appendPrefix(result, QUERY_SEPARATOR, maxBytes, resultBytes);
        }
        if (resultBytes < maxBytes) {
          resultBytes = appendPrefix(result, itemStr, maxBytes, resultBytes);
        }
        first = false;
      }
    }

    return result.length() == 0 ? null : result.toString();
  }

  private static int appendPrefix(
      StringBuilder result, String value, int maxBytes, int resultBytes) {
    int offset = 0;
    while (offset < value.length()) {
      int codePoint = value.codePointAt(offset);
      int codePointBytes = utf8Length(codePoint);
      if (resultBytes + codePointBytes > maxBytes) {
        break;
      }
      int charCount = Character.charCount(codePoint);
      result.append(value, offset, offset + charCount);
      offset += charCount;
      resultBytes += codePointBytes;
    }
    return resultBytes;
  }

  private static int utf8Length(int codePoint) {
    if (codePoint <= 0x7f) {
      return 1;
    }
    if (codePoint <= 0x7ff) {
      return 2;
    }
    if (codePoint <= 0xffff) {
      return Character.isSurrogate((char) codePoint) ? 1 : 3;
    }
    return 4;
  }

  private static boolean causedByQueryBodyLimit(Throwable t) {
    while (t != null) {
      if (t instanceof QueryBodyLimitException) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  private static final class BoundedStringWriter extends Writer {

    private final StringBuilder result;
    private final int maxBytes;
    private int resultBytes;
    private char pendingHighSurrogate;

    private BoundedStringWriter(int maxBytes) {
      this.result = new StringBuilder(Math.min(maxBytes, 1024));
      this.maxBytes = maxBytes;
    }

    @Override
    public void write(char[] buffer, int offset, int length) {
      for (int i = offset; i < offset + length; i++) {
        write(buffer[i]);
      }
    }

    @Override
    public void write(int value) {
      char ch = (char) value;
      if (pendingHighSurrogate != 0) {
        char highSurrogate = pendingHighSurrogate;
        pendingHighSurrogate = 0;
        if (Character.isLowSurrogate(ch)) {
          append(highSurrogate, ch);
          return;
        }
        append(highSurrogate, 1);
      }
      if (Character.isHighSurrogate(ch)) {
        pendingHighSurrogate = ch;
      } else {
        append(ch, utf8Length(ch));
      }
    }

    @Override
    public void write(String value, int offset, int length) {
      for (int i = offset; i < offset + length; i++) {
        write(value.charAt(i));
      }
    }

    private void append(char ch, int bytes) {
      if (resultBytes + bytes > maxBytes) {
        throw new QueryBodyLimitException();
      }
      result.append(ch);
      resultBytes += bytes;
      abortIfFull();
    }

    private void append(char highSurrogate, char lowSurrogate) {
      if (resultBytes + 4 > maxBytes) {
        throw new QueryBodyLimitException();
      }
      result.append(highSurrogate).append(lowSurrogate);
      resultBytes += 4;
      abortIfFull();
    }

    private void abortIfFull() {
      if (resultBytes == maxBytes) {
        throw new QueryBodyLimitException();
      }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
      if (pendingHighSurrogate != 0) {
        char highSurrogate = pendingHighSurrogate;
        pendingHighSurrogate = 0;
        append(highSurrogate, 1);
      }
    }

    @Override
    public String toString() {
      return result.toString();
    }
  }

  private static final class QueryBodyLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private QueryBodyLimitException() {
      super(null, null, false, false);
    }
  }

  private OpenSearchBodyExtractor() {}
}
