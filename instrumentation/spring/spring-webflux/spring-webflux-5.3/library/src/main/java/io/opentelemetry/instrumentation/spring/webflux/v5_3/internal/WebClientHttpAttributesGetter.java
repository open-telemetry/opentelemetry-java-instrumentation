/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum WebClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ClientRequest, ClientResponse> {
  INSTANCE;

  private static final MethodHandle GET_HEADERS;

  static {
    // since webflux 7.0
    MethodHandle methodHandle =
        findGetHeadersMethod(MethodType.methodType(List.class, String.class, List.class));
    if (methodHandle == null) {
      // up to webflux 7.0
      methodHandle =
          findGetHeadersMethod(MethodType.methodType(Object.class, Object.class, Object.class));
    }
    GET_HEADERS = methodHandle;
  }

  private static MethodHandle findGetHeadersMethod(MethodType methodType) {
    try {
      return MethodHandles.lookup().findVirtual(HttpHeaders.class, "getOrDefault", methodType);
    } catch (Throwable t) {
      return null;
    }
  }

  @Override
  public String getUrlFull(ClientRequest request) {
    return request.url().toString();
  }

  @Override
  public String getHttpRequestMethod(ClientRequest request) {
    return request.method().name();
  }

  @Override
  @SuppressWarnings("unchecked") // casting MethodHandle.invoke result
  public List<String> getHttpRequestHeader(ClientRequest request, String name) {
    if (GET_HEADERS != null) {
      try {
        return (List<String>) GET_HEADERS.invoke(request.headers(), name, emptyList());
      } catch (Throwable t) {
        // ignore
      }
    }
    return emptyList();
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

  @Nullable
  @Override
  public String getServerAddress(ClientRequest request) {
    return request.url().getHost();
  }

  @Override
  public Integer getServerPort(ClientRequest request) {
    return request.url().getPort();
  }

  @Nullable
  @Override
  public String getErrorType(
      ClientRequest request, @Nullable ClientResponse response, @Nullable Throwable error) {
    // if both response and error are null it means the request has been cancelled -- see the
    // WebClientTracingFilter class
    if (response == null && error == null) {
      return "cancelled";
    }
    return null;
  }
}
