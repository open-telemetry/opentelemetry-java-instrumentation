/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.logging.Level.FINE;

import com.fasterxml.jackson.core.JsonFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.GenericSerializable;

public final class OpenSearchBodyExtractor {

  private static final Logger logger = Logger.getLogger(OpenSearchBodyExtractor.class.getName());
  private static final String QUERY_SEPARATOR = ";";
  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  @Nullable
  public static String extractSanitized(JsonpMapper mapper, Object request) {
    try {
      if (request instanceof NdJsonpSerializable) {
        return serializeNdJsonSanitized(mapper, (NdJsonpSerializable) request);
      }

      if (request instanceof GenericSerializable) {
        // GenericSerializable writes directly to output stream, cannot sanitize
        // This path is typically not used for search queries
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ((GenericSerializable) request).serialize(baos);
        String body = baos.toString(StandardCharsets.UTF_8);
        return body.isEmpty() ? null : body;
      }

      return serializeSanitized(mapper, request);
    } catch (Exception exception) {
      logger.log(FINE, "Failure extracting body", exception);
      return null;
    }
  }

  @Nullable
  private static String serializeSanitized(JsonpMapper mapper, Object item) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    if (mapper instanceof JacksonJsonpMapper) {
      // Use Jackson-based sanitizing generator for JacksonJsonpMapper
      com.fasterxml.jackson.core.JsonGenerator jacksonGenerator =
          JSON_FACTORY.createGenerator(baos);
      com.fasterxml.jackson.core.JsonGenerator sanitizingGenerator =
          new SanitizingJacksonJsonGenerator(jacksonGenerator);
      try (JsonGenerator generator = new JacksonJsonpGenerator(sanitizingGenerator)) {
        mapper.serialize(item, generator);
      }
    } else {
      // Fallback for other mappers (may not work for all implementations)
      JsonGenerator rawGenerator = mapper.jsonProvider().createGenerator(baos);
      try (JsonGenerator generator = new SanitizingJsonGenerator(rawGenerator)) {
        mapper.serialize(item, generator);
      }
    }

    String result = baos.toString(StandardCharsets.UTF_8).trim();
    return result.isEmpty() ? null : result;
  }

  @Nullable
  private static String serializeNdJsonSanitized(JsonpMapper mapper, NdJsonpSerializable value)
      throws IOException {
    StringBuilder result = new StringBuilder();
    Iterator<?> values = value._serializables();
    boolean first = true;

    while (values.hasNext()) {
      Object item = values.next();
      String itemStr;

      if (item instanceof NdJsonpSerializable && item != value) {
        // Recursively handle nested NdJsonpSerializable
        itemStr = serializeNdJsonSanitized(mapper, (NdJsonpSerializable) item);
      } else {
        itemStr = serializeSanitized(mapper, item);
      }

      if (itemStr != null && !itemStr.isEmpty()) {
        if (!first) {
          result.append(QUERY_SEPARATOR);
        }
        result.append(itemStr);
        first = false;
      }
    }

    return result.length() == 0 ? null : result.toString();
  }

  private OpenSearchBodyExtractor() {}
}
