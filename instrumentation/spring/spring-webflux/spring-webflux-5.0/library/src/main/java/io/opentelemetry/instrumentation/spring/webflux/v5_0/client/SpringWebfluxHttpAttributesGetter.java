/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.client;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

enum SpringWebfluxHttpAttributesGetter
    implements HttpClientAttributesGetter<ClientRequest, ClientResponse> {
  INSTANCE;

  @Override
  public String getUrl(ClientRequest request) {
    return request.url().toString();
  }

  @Nullable
  @Override
  public String getFlavor(ClientRequest request, @Nullable ClientResponse response) {
    return null;
  }

  @Override
  public String getMethod(ClientRequest request) {
    return request.method().name();
  }

  @Override
  public List<String> getRequestHeader(ClientRequest request, String name) {
    return request.headers().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public Integer getStatusCode(
      ClientRequest request, ClientResponse response, @Nullable Throwable error) {
    return StatusCodes.get(response);
  }

  @Override
  public List<String> getResponseHeader(
      ClientRequest request, ClientResponse response, String name) {
    return response.headers().header(name);
  }
}
