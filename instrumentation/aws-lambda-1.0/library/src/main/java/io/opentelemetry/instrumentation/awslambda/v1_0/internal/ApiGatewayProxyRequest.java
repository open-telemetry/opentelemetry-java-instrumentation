/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

import static io.opentelemetry.instrumentation.awslambda.v1_0.internal.HeadersFactory.ofStream;

import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;

public abstract class ApiGatewayProxyRequest {

  // TODO(anuraaga): We should create a RequestFactory type of class instead of evaluating this
  // for every request.
  private static boolean noHttpPropagationNeeded() {
    Collection<String> fields =
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().fields();
    return fields.isEmpty() || xrayPropagationFieldsOnly(fields);
  }

  private static boolean xrayPropagationFieldsOnly(Collection<String> fields) {
    // ugly but faster than typical convert-to-set-and-check-contains-only
    return (fields.size() == 1)
        && ParentContextExtractor.AWS_TRACE_HEADER_PROPAGATOR_KEY.equalsIgnoreCase(
            fields.iterator().next());
  }

  public static ApiGatewayProxyRequest forStream(InputStream source) throws IOException {

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

  private static class CopiedApiGatewayProxyRequest extends ApiGatewayProxyRequest {

    private final byte[] data;

    private CopiedApiGatewayProxyRequest(InputStream inputStream) throws IOException {
      data = IOUtils.toByteArray(inputStream);
    }

    @Override
    public InputStream freshStream() {
      return new ByteArrayInputStream(data);
    }
  }
}
