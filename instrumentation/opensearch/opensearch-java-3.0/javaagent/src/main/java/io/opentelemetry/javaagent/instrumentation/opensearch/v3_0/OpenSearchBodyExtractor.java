/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.logging.Level.FINE;

import jakarta.json.stream.JsonGenerator;
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
  private static final int MAX_QUERY_BODY_LENGTH = 32 * 1024;
  private static final String QUERY_SEPARATOR = ";";

  @Nullable
  public static String extract(JsonpMapper mapper, Object request, boolean sanitize) {
    try {
      if (request instanceof NdJsonpSerializable) {
        return serializeNdJson(
            mapper, (NdJsonpSerializable) request, sanitize, MAX_QUERY_BODY_LENGTH);
      }

      return serialize(mapper, request, sanitize, MAX_QUERY_BODY_LENGTH);
    } catch (RuntimeException e) {
      logger.log(FINE, "Failure extracting body", e);
      return null;
    }
  }

  @Nullable
  private static String serialize(
      JsonpMapper mapper, Object item, boolean sanitize, int maxLength) {
    BoundedStringWriter writer = new BoundedStringWriter(maxLength);

    try {
      if (mapper instanceof JacksonJsonpMapper) {
        JacksonJsonpGenerator jacksonJsonpGenerator =
            (JacksonJsonpGenerator) mapper.jsonProvider().createGenerator(writer);
        com.fasterxml.jackson.core.JsonGenerator jacksonGenerator =
            sanitize
                ? new SanitizingJacksonJsonGenerator(jacksonJsonpGenerator.jacksonGenerator())
                : jacksonJsonpGenerator.jacksonGenerator();
        try (JsonGenerator generator = new JacksonJsonpGenerator(jacksonGenerator)) {
          mapper.serialize(item, generator);
        }
      } else {
        JsonGenerator generator =
            sanitize
                ? new SanitizingJsonGenerator(mapper.jsonProvider().createGenerator(writer))
                : mapper.jsonProvider().createGenerator(writer);
        try (generator) {
          mapper.serialize(item, generator);
        }
      }
    } catch (RuntimeException e) {
      if (!writer.limitReached()) {
        throw e;
      }
    }

    String result = writer.toString().trim();
    return result.isEmpty() ? null : result;
  }

  @Nullable
  private static String serializeNdJson(
      JsonpMapper mapper, NdJsonpSerializable value, boolean sanitize, int maxLength) {
    StringBuilder result = new StringBuilder(Math.min(maxLength, 1024));
    Iterator<?> values = value._serializables();
    boolean first = true;

    while (values.hasNext() && result.length() < maxLength) {
      Object item = values.next();
      String itemStr;
      int remaining = maxLength - result.length();

      if (item instanceof NdJsonpSerializable && item != value) {
        itemStr = serializeNdJson(mapper, (NdJsonpSerializable) item, sanitize, remaining);
      } else {
        itemStr = serialize(mapper, item, sanitize, remaining);
      }

      if (itemStr != null && !itemStr.isEmpty()) {
        if (!first) {
          appendPrefix(result, QUERY_SEPARATOR, maxLength);
        }
        if (result.length() < maxLength) {
          appendPrefix(result, itemStr, maxLength);
        }
        first = false;
      }
    }

    return result.length() == 0 ? null : result.toString();
  }

  private static void appendPrefix(StringBuilder result, String value, int maxLength) {
    int length = Math.min(value.length(), maxLength - result.length());
    result.append(value, 0, length);
  }

  private static final class BoundedStringWriter extends Writer {

    private final StringBuilder result;
    private final int maxLength;
    private boolean limitReached;

    private BoundedStringWriter(int maxLength) {
      this.result = new StringBuilder(Math.min(maxLength, 1024));
      this.maxLength = maxLength;
    }

    @Override
    public void write(char[] buffer, int offset, int length) {
      int writeLength = Math.min(length, maxLength - result.length());
      result.append(buffer, offset, writeLength);
      abortIfFull();
    }

    @Override
    public void write(int value) {
      if (result.length() < maxLength) {
        result.append((char) value);
      }
      abortIfFull();
    }

    @Override
    public void write(String value, int offset, int length) {
      int writeLength = Math.min(length, maxLength - result.length());
      result.append(value, offset, offset + writeLength);
      abortIfFull();
    }

    private void abortIfFull() {
      if (result.length() == maxLength) {
        limitReached = true;
        throw new QueryBodyLimitException();
      }
    }

    private boolean limitReached() {
      return limitReached;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

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
