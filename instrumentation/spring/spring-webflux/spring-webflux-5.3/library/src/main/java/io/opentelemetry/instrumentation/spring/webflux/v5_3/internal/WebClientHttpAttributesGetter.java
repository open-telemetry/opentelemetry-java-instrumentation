/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

enum WebClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ClientRequest, ClientResponse> {
  INSTANCE;

  @Override
  public String getUrlFull(ClientRequest request) {
    return request.url().toString();
  }

  @Override
  public String getHttpRequestMethod(ClientRequest request) {
    return request.method().name();
  }

  @Override
  public List<String> getHttpRequestHeader(ClientRequest request, String name) {
    return request.headers().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      ClientRequest request, ClientResponse response, @Nullable Throwable error) {
    return StatusCodes.get(response);
  }

  @Override
  public List<String> getHttpResponseHeader(
      ClientRequest request, ClientResponse response, String name) {
    return response.headers().header(name);
  }
}
