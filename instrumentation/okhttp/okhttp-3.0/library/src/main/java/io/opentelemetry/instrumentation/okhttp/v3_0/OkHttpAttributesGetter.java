/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Request;
import okhttp3.Response;

final class OkHttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {

  @Override
  public String method(Request request) {
    return request.method();
  }

  @Override
  public String url(Request request) {
    return request.url().toString();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    return request.headers(name);
  }

  @Override
  @Nullable
  public Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @SuppressWarnings("UnnecessaryDefaultInEnumSwitch")
  @Nullable
  public String flavor(Request request, @Nullable Response response) {
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
  public Integer statusCode(Request request, Response response) {
    return response.code();
  }

  @Override
  @Nullable
  public Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  public List<String> responseHeader(Request request, Response response, String name) {
    return response.headers(name);
  }
}
