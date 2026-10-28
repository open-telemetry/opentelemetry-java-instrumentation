/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

class SpringWebHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, ClientHttpResponse> {

  @Nullable private static final MethodHandle GET_STATUS_CODE;
  @Nullable private static final MethodHandle STATUS_CODE_VALUE;

  static {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    MethodHandle getStatusCode = null;
    MethodHandle statusCodeValue = null;
    // Spring 6 changed the return type from HttpStatus to HttpStatusCode.
    for (String typeName :
        new String[] {
          "org.springframework.http.HttpStatusCode", "org.springframework.http.HttpStatus"
        }) {
      try {
        Class<?> statusType =
            Class.forName(typeName, false, ClientHttpResponse.class.getClassLoader());
        getStatusCode =
            lookup.findVirtual(
                ClientHttpResponse.class, "getStatusCode", MethodType.methodType(statusType));
        statusCodeValue = lookup.findVirtual(statusType, "value", MethodType.methodType(int.class));
        break;
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ignored) {
        // Try the other signature.
      }
    }

    GET_STATUS_CODE = getStatusCode;
    STATUS_CODE_VALUE = statusCodeValue;
  }

  @Override
  public String getHttpRequestMethod(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  public String getUrlFull(HttpRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest httpRequest, String name) {
    return HeaderUtil.getHeader(httpRequest.getHeaders(), name);
  }

  @Override
  public Collection<String> getHttpRequestHeaderNames(HttpRequest httpRequest) {
    return HeaderUtil.getHeaderNames(httpRequest.getHeaders());
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, @Nullable Throwable error) {

    if (GET_STATUS_CODE == null || STATUS_CODE_VALUE == null) {
      return null;
    }

    try {
      Object statusCode = GET_STATUS_CODE.invoke(clientHttpResponse);
      return (int) STATUS_CODE_VALUE.invoke(statusCode);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, String name) {
    return HeaderUtil.getHeader(clientHttpResponse.getHeaders(), name);
  }

  @Override
  public Collection<String> getHttpResponseHeaderNames(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    return HeaderUtil.getHeaderNames(clientHttpResponse.getHeaders());
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest httpRequest) {
    return httpRequest.getURI().getHost();
  }

  @Override
  @Nullable
  public Integer getServerPort(HttpRequest httpRequest) {
    URI uri = httpRequest.getURI();
    return HttpConstants.portOrDefaultFromScheme(uri.getPort(), uri.getScheme());
  }
}
