/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINE;

import com.fasterxml.jackson.core.JsonFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;

class OpenSearchBodyExtractor {

  private static final Logger logger = Logger.getLogger(OpenSearchBodyExtractor.class.getName());
  private static final String QUERY_SEPARATOR = ";";
  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  @Nullable
  public static String extract(JsonpMapper mapper, Object request, boolean sanitize) {
    try {
      if (request instanceof NdJsonpSerializable) {
        return serializeNdJson(mapper, (NdJsonpSerializable) request, sanitize);
      }

      return serialize(mapper, request, sanitize);
    } catch (Exception e) {
      logger.log(FINE, "Failure extracting body", e);
      return null;
    }
  }

  @Nullable
  private static String serialize(JsonpMapper mapper, Object item, boolean sanitize)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    if (mapper instanceof JacksonJsonpMapper) {
      com.fasterxml.jackson.core.JsonGenerator jacksonGenerator =
          sanitize
              ? new SanitizingJacksonJsonGenerator(JSON_FACTORY.createGenerator(baos))
              : JSON_FACTORY.createGenerator(baos);
      try (JsonGenerator generator = new JacksonJsonpGenerator(jacksonGenerator)) {
        mapper.serialize(item, generator);
      }
    } else {
      JsonGenerator generator =
          sanitize
              ? new SanitizingJsonGenerator(mapper.jsonProvider().createGenerator(baos))
              : mapper.jsonProvider().createGenerator(baos);
      try (generator) {
        mapper.serialize(item, generator);
      }
    }

    String result = baos.toString(UTF_8).trim();
    return result.isEmpty() ? null : result;
  }

  @Nullable
  private static String serializeNdJson(
      JsonpMapper mapper, NdJsonpSerializable value, boolean sanitize) throws IOException {
    StringBuilder result = new StringBuilder();
    Iterator<?> values = value._serializables();
    boolean first = true;

    while (values.hasNext()) {
      Object item = values.next();
      String itemStr;

      if (item instanceof NdJsonpSerializable && item != value) {
        itemStr = serializeNdJson(mapper, (NdJsonpSerializable) item, sanitize);
      } else {
        itemStr = serialize(mapper, item, sanitize);
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
