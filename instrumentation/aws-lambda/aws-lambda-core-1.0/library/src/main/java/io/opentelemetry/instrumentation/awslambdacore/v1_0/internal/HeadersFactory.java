/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class HeadersFactory {

  private static final Logger logger = Logger.getLogger(HeadersFactory.class.getName());

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  static Map<String, String> ofStream(InputStream inputStream) {
    try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
      parser.nextToken();

      if (!parser.isExpectedStartObjectToken()) {
        logger.fine("Not a JSON object");
        return Collections.emptyMap();
      }
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        parser.nextToken();
        if (!parser.getCurrentName().equals("headers")) {
          parser.skipChildren();
          continue;
        }

        if (!parser.isExpectedStartObjectToken()) {
          logger.fine("Invalid JSON for HTTP headers");
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
      logger.log(Level.FINE, "Could not get headers from request", e);
    }
    return Collections.emptyMap();
  }

  private HeadersFactory() {}
}
