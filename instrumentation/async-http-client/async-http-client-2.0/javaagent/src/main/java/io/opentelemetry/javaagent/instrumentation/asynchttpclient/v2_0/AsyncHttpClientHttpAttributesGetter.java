/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import org.asynchttpclient.Response;

final class AsyncHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<RequestContext, Response> {

  @Override
  public String method(RequestContext requestContext) {
    return requestContext.getRequest().getMethod();
  }

  @Override
  public String url(RequestContext requestContext) {
    return requestContext.getRequest().getUri().toUrl();
  }

  @Override
  public List<String> requestHeader(RequestContext requestContext, String name) {
    return requestContext.getRequest().getHeaders().getAll(name);
  }

  @Override
  public Integer statusCode(
      RequestContext requestContext, Response response, @Nullable Throwable error) {
    return response.getStatusCode();
  }

  @Override
  public String flavor(RequestContext requestContext, @Nullable Response response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public List<String> responseHeader(
      RequestContext requestContext, Response response, String name) {
    return response.getHeaders().getAll(name);
  }
}
