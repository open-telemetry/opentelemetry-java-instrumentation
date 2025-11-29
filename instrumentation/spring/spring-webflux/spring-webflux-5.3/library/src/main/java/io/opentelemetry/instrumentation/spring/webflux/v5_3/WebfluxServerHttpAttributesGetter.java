/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.HeaderUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

enum WebfluxServerHttpAttributesGetter
    implements HttpServerAttributesGetter<ServerWebExchange, ServerWebExchange> {
  INSTANCE;

  @Nullable private static final MethodHandle GET_RAW_STATUS_CODE;
  @Nullable private static final MethodHandle GET_STATUS_CODE;
  @Nullable private static final MethodHandle STATUS_CODE_VALUE;

  static {
    MethodHandle getRawStatusCode = null;
    MethodHandle getStatusCode = null;
    MethodHandle statusCodeValue = null;

    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    // up to webflux 7.0
    try {
      getRawStatusCode =
          lookup.findVirtual(
              ServerHttpResponse.class, "getRawStatusCode", MethodType.methodType(Integer.class));
    } catch (Exception exception) {
      // ignore
    }

    // since webflux 7.0
    try {
      Class<?> httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
      getStatusCode =
          lookup.findVirtual(
              ServerHttpResponse.class,
              "getStatusCode",
              MethodType.methodType(httpStatusCodeClass));
      statusCodeValue =
          lookup.findVirtual(httpStatusCodeClass, "value", MethodType.methodType(int.class));
    } catch (Exception exception) {
      // ignore
    }

    GET_RAW_STATUS_CODE = getRawStatusCode;
    GET_STATUS_CODE = getStatusCode;
    STATUS_CODE_VALUE = statusCodeValue;
  }

  @Nullable
  private static Integer getStatusCode(ServerHttpResponse response) {
    if (GET_RAW_STATUS_CODE != null) {
      try {
        return (Integer) GET_RAW_STATUS_CODE.invoke(response);
      } catch (Throwable e) {
        // ignore
      }
    }
    if (GET_STATUS_CODE != null && STATUS_CODE_VALUE != null) {
      try {
        Object statusCode = GET_STATUS_CODE.invoke(response);
        return (Integer) STATUS_CODE_VALUE.invoke(statusCode);
      } catch (Throwable e) {
        // ignore
      }
    }
    return null;
  }

  @Override
  public String getHttpRequestMethod(ServerWebExchange request) {
    return request.getRequest().getMethod().name();
  }

  @Override
  public List<String> getHttpRequestHeader(ServerWebExchange request, String name) {
    return HeaderUtil.getHeader(request.getRequest().getHeaders(), name);
  }

  @Nullable
  @Override
  public Integer getHttpResponseStatusCode(
      ServerWebExchange request, ServerWebExchange response, @Nullable Throwable error) {
    return getStatusCode(response.getResponse());
  }

  @Override
  public List<String> getHttpResponseHeader(
      ServerWebExchange request, ServerWebExchange response, String name) {
    return HeaderUtil.getHeader(response.getResponse().getHeaders(), name);
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
