/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

enum SpringWebHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, ClientHttpResponse> {
  INSTANCE;

  @Nullable private static final MethodHandle GET_STATUS_CODE;
  @Nullable private static final MethodHandle STATUS_CODE_VALUE;
  private static final MethodHandle GET_HEADERS;

  static {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    MethodHandle getStatusCode = null;
    MethodHandle statusCodeValue = null;
    Class<?> httpStatusCodeClass = null;

    try {
      httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
    } catch (ClassNotFoundException e) {
      try {
        httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatus");
      } catch (ClassNotFoundException ignored) {
        // ignored
      }
    }

    if (httpStatusCodeClass != null) {
      try {
        getStatusCode =
            lookup.findVirtual(
                ClientHttpResponse.class,
                "getStatusCode",
                MethodType.methodType(httpStatusCodeClass));
        statusCodeValue =
            lookup.findVirtual(httpStatusCodeClass, "value", MethodType.methodType(int.class));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        // ignored
      }
    }

    GET_STATUS_CODE = getStatusCode;
    STATUS_CODE_VALUE = statusCodeValue;
    GET_HEADERS =
        isSpring7OrNewer()
            ? findGetHeadersMethod(MethodType.methodType(List.class, String.class))
            : findGetHeadersMethod(MethodType.methodType(List.class, Object.class));
  }

  private static boolean isSpring7OrNewer() {
    try {
      Class.forName("org.springframework.core.Nullness");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static MethodHandle findGetHeadersMethod(MethodType methodType) {
    try {
      return MethodHandles.lookup().findVirtual(HttpHeaders.class, "get", methodType);
    } catch (Throwable t) {
      return null;
    }
  }

  @Override
  public String getHttpRequestMethod(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  @Nullable
  public String getUrlFull(HttpRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest httpRequest, String name) {
    return getHeader(httpRequest.getHeaders(), name);
  }

  @SuppressWarnings("unchecked") // casting MethodHandle.invoke result
  static List<String> getHeader(HttpHeaders headers, String name) {
    try {
      List<String> result = (List<String>) GET_HEADERS.invoke(headers, name);
      if (result == null) {
        return emptyList();
      }
      return result;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, @Nullable Throwable error) {

    if (GET_STATUS_CODE == null || STATUS_CODE_VALUE == null) {
      return null;
    }

    try {
      Object statusCode = GET_STATUS_CODE.invoke(clientHttpResponse);
      return (int) STATUS_CODE_VALUE.invoke(statusCode);
    } catch (Throwable e) {
      return null;
    }
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, String name) {
    return getHeader(clientHttpResponse.getHeaders(), name);
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest httpRequest) {
    return httpRequest.getURI().getHost();
  }

  @Override
  public Integer getServerPort(HttpRequest httpRequest) {
    return httpRequest.getURI().getPort();
  }
}
