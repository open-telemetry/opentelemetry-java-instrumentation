/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HeadersFactory {

  private static final Logger logger = LoggerFactory.getLogger(HeadersFactory.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Nullable
  static Map<String, String> ofStream(InputStream inputStream) {
    try (JsonParser jParser = new JsonFactory().createParser(inputStream)) {
      while (jParser.nextToken() != null) {
        String name = jParser.getCurrentName();
        if ("headers".equalsIgnoreCase(name)) {
          jParser.nextToken();
          return OBJECT_MAPPER.readValue(jParser, Map.class);
        }
      }
    } catch (Exception e) {
      logger.debug("Could not get headers from request, ", e);
    }
    return null;
  }

  private HeadersFactory() {}
}
