/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

enum WebfluxServerHttpAttributesGetter
    implements HttpServerAttributesGetter<ServerWebExchange, ServerWebExchange> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(ServerWebExchange request) {
    return request.getRequest().getMethodValue();
  }

  @Override
  public List<String> getHttpRequestHeader(ServerWebExchange request, String name) {
    return request.getRequest().getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Nullable
  @Override
  public Integer getHttpResponseStatusCode(
      ServerWebExchange request, ServerWebExchange response, @Nullable Throwable error) {
    return response.getResponse().getRawStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      ServerWebExchange request, ServerWebExchange response, String name) {
    return response.getResponse().getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Nullable
  @Override
  public String getUrlScheme(ServerWebExchange request) {
    return request.getRequest().getURI().getScheme();
  }

  @Nullable
  @Override
  public String getUrlPath(ServerWebExchange request) {
    return request.getRequest().getURI().getPath();
  }

  @Nullable
  @Override
  public String getUrlQuery(ServerWebExchange request) {
    return request.getRequest().getURI().getQuery();
  }

  @Nullable
  @Override
  public String getHttpRoute(ServerWebExchange request) {
    Object bestPatternObj = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (bestPatternObj == null) {
      return null;
    }
    String route;
    if (bestPatternObj instanceof PathPattern) {
      route = ((PathPattern) bestPatternObj).getPatternString();
    } else {
      route = bestPatternObj.toString();
    }
    if (route.equals("/**")) {
      return null;
    }
    String contextPath = request.getRequest().getPath().contextPath().value();
    return contextPath + (route.startsWith("/") ? route : ("/" + route));
  }

  @Nullable
  @Override
  public String getServerAddress(ServerWebExchange request) {
    return request.getRequest().getURI().getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(ServerWebExchange request) {
    int port = request.getRequest().getURI().getPort();
    return port == -1 ? null : port;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ServerWebExchange request, @Nullable ServerWebExchange response) {
    return request.getRequest().getRemoteAddress();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      ServerWebExchange request, @Nullable ServerWebExchange response) {
    return request.getRequest().getLocalAddress();
  }
}
