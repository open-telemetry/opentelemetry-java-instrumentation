/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

final class OkHttpAttributesExtractor extends HttpAttributesExtractor<Request, Response> {
  @Override
  protected String method(Request request) {
    return request.method();
  }

  @Override
  protected String url(Request request) {
    return request.url().toString();
  }

  @Override
  protected @Nullable String target(Request request) {
    return null;
  }

  @Override
  protected String host(Request request) {
    return request.url().host();
  }

  @Override
  protected @Nullable String route(Request request) {
    return null;
  }

  @Override
  protected String scheme(Request request) {
    return request.url().scheme();
  }

  @Override
  protected @Nullable String userAgent(Request request) {
    // using lowercase header name intentionally to ensure extraction is not case-sensitive
    return request.header("user-agent");
  }

  @Override
  protected @Nullable Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @SuppressWarnings("UnnecessaryDefaultInEnumSwitch")
  protected @Nullable String flavor(Request request, @Nullable Response response) {
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
        // No OTel mapping for other protocols like H2C.
      default:
        return null;
    }
  }

  @Override
  protected @Nullable String serverName(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected Integer statusCode(Request request, Response response) {
    return response.code();
  }

  @Override
  protected @Nullable Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }
}
