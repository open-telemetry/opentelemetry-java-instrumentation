/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.serverless.proxy.model.Headers;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import java.io.InputStream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ApiGatewayProxyRequest {

  private static final Logger log = LoggerFactory.getLogger(ApiGatewayProxyRequest.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.registerModule(new AfterburnerModule());
  }

  private final Headers headers;

  private ApiGatewayProxyRequest(Headers headers) {
    this.headers = headers;
  }

  static ApiGatewayProxyRequest ofInputStream(InputStream is) {

    try (JsonParser jParser = new JsonFactory().createParser(is)) {

      Headers headers = null;
      while (jParser.nextToken() != null && headers == null) {
        String name = jParser.getCurrentName();
        if ("multiValueHeaders".equalsIgnoreCase(name)) {
          jParser.nextToken();
          headers = OBJECT_MAPPER.readValue(jParser, Headers.class);
        }
      }

      return new ApiGatewayProxyRequest(headers);
    } catch (Exception e) {
      log.debug("Could not get headers from request, ", e);
    }

    return new ApiGatewayProxyRequest(null);
  }

  @Nullable
  public Headers getHeaders() {
    return headers;
  }
}
