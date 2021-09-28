/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class OkHttp2HttpAttributesExtractor
    extends HttpClientAttributesExtractor<Request, Response> {

  @Override
  protected String method(Request request) {
    return request.method();
  }

  @Override
  protected String url(Request request) {
    return request.urlString();
  }

  @Override
  @Nullable
  protected String userAgent(Request request) {
    return request.header("User-Agent");
  }

  @Override
  @Nullable
  protected Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected Integer statusCode(Request request, Response response) {
    return response.code();
  }

  @Override
  @Nullable
  protected String flavor(Request request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    switch (response.protocol()) {
      case HTTP_1_0:
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case HTTP_1_1:
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
      case HTTP_2:
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
      case SPDY_3:
        return SemanticAttributes.HttpFlavorValues.SPDY;
    }
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLength(Request request, Response response) {
    return response.body().contentLength();
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }
}
