/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.asynchttpclient.Response;

final class AsyncHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<RequestContext, Response> {

  @Override
  public String getHttpRequestMethod(RequestContext requestContext) {
    return requestContext.getRequest().getMethod();
  }

  @Override
  public String getUrlFull(RequestContext requestContext) {
    return requestContext.getRequest().getUri().toUrl();
  }

  @Override
  public List<String> getHttpRequestHeader(RequestContext requestContext, String name) {
    return requestContext.getRequest().getHeaders().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      RequestContext requestContext, Response response, @Nullable Throwable error) {
    return response.getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      RequestContext requestContext, Response response, String name) {
    return response.getHeaders().getAll(name);
  }
}
