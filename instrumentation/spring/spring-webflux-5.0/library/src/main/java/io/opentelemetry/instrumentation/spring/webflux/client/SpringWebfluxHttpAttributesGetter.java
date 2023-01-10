/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

enum SpringWebfluxHttpAttributesGetter
    implements HttpClientAttributesGetter<ClientRequest, ClientResponse> {
  INSTANCE;

  private static final MethodHandle RAW_STATUS_CODE = findRawStatusCode();
  private static final MethodHandle FALLBACK_STATUS_CODE = findFallbackStatusCode();

  // rawStatusCode() method was introduced in webflux 5.1
  // prior to this method, the best we can get is HttpStatus enum, which only covers standard status
  // codes (see usage below)
  private static MethodHandle findRawStatusCode() {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(ClientResponse.class, "rawStatusCode", MethodType.methodType(int.class));
    } catch (IllegalAccessException | NoSuchMethodException e) {
      return null;
    }
  }

  private static MethodHandle findFallbackStatusCode() {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(ClientResponse.class, "statusCode", MethodType.methodType(HttpStatus.class));
    } catch (IllegalAccessException | NoSuchMethodException e) {
      return null;
    }
  }

  @Override
  public String url(ClientRequest request) {
    return request.url().toString();
  }

  @Nullable
  @Override
  public String flavor(ClientRequest request, @Nullable ClientResponse response) {
    return null;
  }

  @Override
  public String method(ClientRequest request) {
    return request.method().name();
  }

  @Override
  public List<String> requestHeader(ClientRequest request, String name) {
    return request.headers().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public Integer statusCode(
      ClientRequest request, ClientResponse response, @Nullable Throwable error) {
    if (RAW_STATUS_CODE != null) {
      // rawStatusCode() method was introduced in webflux 5.1
      try {
        return (int) RAW_STATUS_CODE.invokeExact(response);
      } catch (Throwable ignored) {
        // Ignore
      }
    }
    // prior to webflux 5.1, the best we can get is HttpStatus enum, which only covers standard
    // status codes (note: this method was removed in webflux 6.0)
    if (FALLBACK_STATUS_CODE != null) {
      try {
        HttpStatus httpStatus = (HttpStatus) FALLBACK_STATUS_CODE.invokeExact(response);
        return httpStatus.value();
      } catch (Throwable e) {
        // Ignore
      }
    }
    return null;
  }

  @Override
  public List<String> responseHeader(ClientRequest request, ClientResponse response, String name) {
    return response.headers().header(name);
  }
}
