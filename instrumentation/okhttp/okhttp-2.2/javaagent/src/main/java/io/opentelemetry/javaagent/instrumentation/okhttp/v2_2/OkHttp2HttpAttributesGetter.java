/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

final class OkHttp2HttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {

  @Override
  public String method(Request request) {
    return request.method();
  }

  @Override
  public String url(Request request) {
    return request.urlString();
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
  public Integer statusCode(Request request, Response response) {
    return response.code();
  }

  @Override
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
    }
    return null;
  }

  @Override
  public Long responseContentLength(Request request, Response response) {
    return response.body().contentLength();
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
