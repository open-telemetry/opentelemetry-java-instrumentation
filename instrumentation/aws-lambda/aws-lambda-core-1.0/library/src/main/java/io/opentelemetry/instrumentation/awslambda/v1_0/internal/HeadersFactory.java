/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HeadersFactory {

  private static final Logger logger = LoggerFactory.getLogger(HeadersFactory.class);

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  static Map<String, String> ofStream(InputStream inputStream) {
    try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
      parser.nextToken();

      if (!parser.isExpectedStartObjectToken()) {
        logger.debug("Not a JSON object");
        return Collections.emptyMap();
      }
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        parser.nextToken();
        if (!parser.getCurrentName().equals("headers")) {
          parser.skipChildren();
          continue;
        }

        if (!parser.isExpectedStartObjectToken()) {
          logger.debug("Invalid JSON for HTTP headers");
          return Collections.emptyMap();
        }

        Map<String, String> headers = new HashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          String value = parser.nextTextValue();
          if (value != null) {
            headers.put(parser.getCurrentName(), value);
          }
        }
        return headers;
      }
    } catch (Exception e) {
      logger.debug("Could not get headers from request, ", e);
    }
    return Collections.emptyMap();
  }

  private HeadersFactory() {}
}
