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
      throw new IllegalStateException(t);
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
    try {
      List<String> result = (List<String>) GET_HEADERS.invoke(request.headers(), name);
      if (result == null) {
        return emptyList();
      }
      return result;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
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
