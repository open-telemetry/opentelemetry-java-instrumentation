/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.HeadersFactory.ofStream;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class ApiGatewayProxyRequest {

  private static final Logger logger = Logger.getLogger(ApiGatewayProxyRequest.class.getName());

  // TODO: We should create a RequestFactory type of class instead of evaluating this for every
  // request.
  private static boolean noHttpPropagationNeeded() {
    Collection<String> fields =
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().fields();
    return fields.isEmpty();
  }

  public static ApiGatewayProxyRequest forStream(InputStream source) {
    if (noHttpPropagationNeeded()) {
      return new NoopRequest(source);
    }

    if (source.markSupported()) {
      return new MarkableApiGatewayProxyRequest(source);
    }
    // It is known that the Lambda runtime passes ByteArrayInputStream's to functions, so gracefully
    // handle this without propagating and revisit if getting user reports that expectations
    // changed.
    logger.log(
        WARNING,
        "HTTP propagation enabled but could not extract HTTP headers."
            + " This is a bug in the OpenTelemetry AWS Lambda instrumentation."
            + " Type of request stream {0}",
        source.getClass());
    return new NoopRequest(source);
  }

  @Nullable
  public Map<String, String> getHeaders() throws IOException {
    Map<String, String> headers = ofStream(freshStream());
    return (headers == null ? Collections.emptyMap() : headers);
  }

  public abstract InputStream freshStream() throws IOException;

  private static class NoopRequest extends ApiGatewayProxyRequest {

    private final InputStream stream;

    private NoopRequest(InputStream stream) {
      this.stream = stream;
    }

    @Override
    public InputStream freshStream() {
      return stream;
    }

    @Override
    public Map<String, String> getHeaders() {
      return Collections.emptyMap();
    }
  }

  private static class MarkableApiGatewayProxyRequest extends ApiGatewayProxyRequest {

    private final InputStream inputStream;

    private MarkableApiGatewayProxyRequest(InputStream inputStream) {
      this.inputStream = inputStream;
      inputStream.mark(Integer.MAX_VALUE);
    }

    @Override
    public InputStream freshStream() throws IOException {
      inputStream.reset();
      inputStream.mark(Integer.MAX_VALUE);
      return inputStream;
    }
  }
}
