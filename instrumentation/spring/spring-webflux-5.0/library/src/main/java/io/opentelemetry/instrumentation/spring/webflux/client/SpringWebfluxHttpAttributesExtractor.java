/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

final class SpringWebfluxHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ClientRequest, ClientResponse> {

  private static final MethodHandle RAW_STATUS_CODE = findRawStatusCode();

  // rawStatusCode() method was introduced in webflux 5.1
  // prior to this method, the best we can get is HttpStatus enum, which only covers standard status
  // codes (see usage above)
  private static MethodHandle findRawStatusCode() {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(ClientResponse.class, "rawStatusCode", MethodType.methodType(int.class));
    } catch (IllegalAccessException | NoSuchMethodException e) {
      return null;
    }
  }

  SpringWebfluxHttpAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
    super(capturedHttpHeaders);
  }

  @Override
  protected String url(ClientRequest request) {
    return request.url().toString();
  }

  @Nullable
  @Override
  protected String flavor(ClientRequest request, @Nullable ClientResponse response) {
    return null;
  }

  @Override
  protected String method(ClientRequest request) {
    return request.method().name();
  }

  @Override
  protected List<String> requestHeader(ClientRequest request, String name) {
    return request.headers().getOrDefault(name, emptyList());
  }

  @Nullable
  @Override
  protected Long requestContentLength(ClientRequest request, @Nullable ClientResponse response) {
    return null;
  }

  @Nullable
  @Override
  protected Long requestContentLengthUncompressed(
      ClientRequest request, @Nullable ClientResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(ClientRequest request, ClientResponse response) {
    if (RAW_STATUS_CODE != null) {
      // rawStatusCode() method was introduced in webflux 5.1
      try {
        return (int) RAW_STATUS_CODE.invokeExact(response);
      } catch (Throwable ignored) {
        // Ignore
      }
    }
    // prior to webflux 5.1, the best we can get is HttpStatus enum, which only covers standard
    // status codes
    return response.statusCode().value();
  }

  @Nullable
  @Override
  protected Long responseContentLength(ClientRequest request, ClientResponse response) {
    return null;
  }

  @Nullable
  @Override
  protected Long responseContentLengthUncompressed(ClientRequest request, ClientResponse response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      ClientRequest request, ClientResponse response, String name) {
    return response.headers().header(name);
  }
}
