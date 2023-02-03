/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

enum SpringWebfluxHttpAttributesGetter
    implements HttpServerAttributesGetter<ServerHttpRequest, ServerHttpResponse> {
  INSTANCE;

  @Override
  public String getMethod(ServerHttpRequest request) {
    return request.getMethodValue();
  }

  @Override
  public List<String> getRequestHeader(ServerHttpRequest request, String name) {
    return request.getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Nullable
  @Override
  public Integer getStatusCode(
      ServerHttpRequest request, ServerHttpResponse response, @Nullable Throwable error) {
    HttpStatus status = response.getStatusCode();
    return status == null ? null : status.value();
  }

  @Override
  public List<String> getResponseHeader(
      ServerHttpRequest request, ServerHttpResponse response, String name) {
    return response.getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Nullable
  @Override
  public String getFlavor(ServerHttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getTarget(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    String query = request.getURI().getQuery();
    if (path == null && query == null) {
      return null;
    }
    if (query != null) {
      query = "?" + query;
    }
    return Optional.ofNullable(query).orElse("") + Optional.ofNullable(query).orElse("");
  }

  @Nullable
  @Override
  public String getRoute(ServerHttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getScheme(ServerHttpRequest request) {
    return request.getURI().getScheme();
  }
}
