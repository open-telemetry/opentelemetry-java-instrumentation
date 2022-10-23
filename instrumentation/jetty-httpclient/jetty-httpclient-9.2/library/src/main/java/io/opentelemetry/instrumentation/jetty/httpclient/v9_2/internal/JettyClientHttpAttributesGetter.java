/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_0;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_1;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpVersion;

enum JettyClientHttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  @Nullable
  public String method(Request request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  public String url(Request request) {
    return request.getURI().toString();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    return request.getHeaders().getValuesList(name);
  }

  @Override
  public String flavor(Request request, @Nullable Response response) {

    if (response == null) {
      return HTTP_1_1;
    }
    HttpVersion httpVersion = response.getVersion();
    httpVersion = (httpVersion != null) ? httpVersion : HttpVersion.HTTP_1_1;
    switch (httpVersion) {
      case HTTP_0_9:
      case HTTP_1_0:
        return HTTP_1_0;
      case HTTP_1_1:
        return HTTP_1_1;
      default:
        // version 2.0 enum name difference in later versions 9.2 and 9.4 versions
        if (httpVersion.toString().endsWith("2.0")) {
          return HTTP_2_0;
        }

        return HTTP_1_1;
    }
  }

  @Override
  public Integer statusCode(Request request, Response response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> responseHeader(Request request, Response response, String name) {
    return response.getHeaders().getValuesList(name);
  }
}
