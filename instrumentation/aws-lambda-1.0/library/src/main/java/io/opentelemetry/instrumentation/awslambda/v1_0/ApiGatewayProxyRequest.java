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
import io.opentelemetry.api.OpenTelemetry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class ApiGatewayProxyRequest {

  private static final Logger log = LoggerFactory.getLogger(ApiGatewayProxyRequest.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.registerModule(new AfterburnerModule());
  }

  private static boolean noHttpPropagationNeeded() {
    return OpenTelemetry.getGlobalPropagators().getTextMapPropagator().fields().isEmpty();
  }

  static ApiGatewayProxyRequest forStream(final InputStream source) throws IOException {

    if (noHttpPropagationNeeded()) {
      return new NoopRequest(source);
    }

    if (source.markSupported()) {
      return new MarkableApiGatewayProxyRequest(source);
    }
    // fallback
    return new CopiedApiGatewayProxyRequest(source);
  }

  @Nullable
  Headers getHeaders() {
    try (JsonParser jParser = new JsonFactory().createParser(freshStream())) {

      Headers headers = null;
      while (jParser.nextToken() != null && headers == null) {
        String name = jParser.getCurrentName();
        if ("multiValueHeaders".equalsIgnoreCase(name)) {
          jParser.nextToken();
          return OBJECT_MAPPER.readValue(jParser, Headers.class);
        }
      }

    } catch (Exception e) {
      log.debug("Could not get headers from request, ", e);
    }
    return null;
  }

  abstract InputStream freshStream() throws IOException;

  private static class NoopRequest extends ApiGatewayProxyRequest {

    private final InputStream stream;

    private NoopRequest(InputStream stream) {
      this.stream = stream;
    }

    @Override
    InputStream freshStream() {
      return stream;
    }

    @Override
    Headers getHeaders() {
      return null;
    }
  }

  private static class MarkableApiGatewayProxyRequest extends ApiGatewayProxyRequest {

    private final InputStream inputStream;

    private MarkableApiGatewayProxyRequest(InputStream inputStream) {
      this.inputStream = inputStream;
      inputStream.mark(Integer.MAX_VALUE);
    }

    @Override
    InputStream freshStream() throws IOException {

      inputStream.reset();
      inputStream.mark(Integer.MAX_VALUE);
      return inputStream;
    }
  }

  private static class CopiedApiGatewayProxyRequest extends ApiGatewayProxyRequest {

    private final byte[] data;

    private CopiedApiGatewayProxyRequest(InputStream inputStream) throws IOException {
      data = IOUtils.toByteArray(inputStream);
    }

    @Override
    InputStream freshStream() {
      return new ByteArrayInputStream(data);
    }
  }
}
