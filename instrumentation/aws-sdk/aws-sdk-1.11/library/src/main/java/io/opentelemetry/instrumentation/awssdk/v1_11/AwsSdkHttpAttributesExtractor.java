/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

class AwsSdkHttpAttributesExtractor extends HttpClientAttributesExtractor<Request<?>, Response<?>> {
  @Override
  protected String url(Request<?> request) {
    return request.getEndpoint().toString();
  }

  @Override
  @Nullable
  protected String flavor(Request<?> request, @Nullable Response<?> response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  protected String method(Request<?> request) {
    return request.getHttpMethod().name();
  }

  @Override
  protected List<String> requestHeader(Request<?> request, String name) {
    String value = request.getHeaders().get(name.equals("user-agent") ? "User-Agent" : name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  @Nullable
  protected Long requestContentLength(Request<?> request, @Nullable Response<?> response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      Request<?> request, @Nullable Response<?> response) {
    return null;
  }

  @Override
  protected Integer statusCode(Request<?> request, Response<?> response) {
    return response.getHttpResponse().getStatusCode();
  }

  @Override
  @Nullable
  protected Long responseContentLength(Request<?> request, Response<?> response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(Request<?> request, Response<?> response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(Request<?> request, Response<?> response, String name) {
    String value = response.getHttpResponse().getHeaders().get(name);
    return value == null ? emptyList() : singletonList(value);
  }
}
