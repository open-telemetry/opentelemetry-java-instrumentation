/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.SemanticAttributes;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

public abstract class AbstractSpringWebfluxClientInstrumentationTest
    extends AbstractHttpClientTest<WebClient.RequestBodySpec> {

  @Override
  public WebClient.RequestBodySpec buildRequest(
      String method, URI uri, Map<String, String> headers) {

    WebClient webClient =
        instrument(WebClient.builder().clientConnector(ClientHttpConnectorFactory.create()))
            .build();

    return webClient
        .method(HttpMethod.valueOf(method))
        .uri(uri)
        .headers(h -> headers.forEach(h::add));
  }

  protected abstract WebClient.Builder instrument(WebClient.Builder builder);

  @Override
  public int sendRequest(
      WebClient.RequestBodySpec request, String method, URI uri, Map<String, String> headers) {
    ClientResponse response = requireNonNull(request.exchange().block());
    return getStatusCode(response);
  }

  @Override
  public void sendRequestWithCallback(
      WebClient.RequestBodySpec request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    request
        .exchange()
        .subscribe(
            response -> httpClientResult.complete(getStatusCode(response)),
            httpClientResult::complete);
  }

  @Override
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();

    // timeouts leak the scope
    optionsBuilder.disableTestReadTimeout();

    if (SemconvStability.emitOldHttpSemconv()) {
      optionsBuilder.setHttpAttributes(
          uri -> {
            Set<AttributeKey<?>> attributes =
                new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
            attributes.remove(SemanticAttributes.NET_PROTOCOL_NAME);
            attributes.remove(SemanticAttributes.NET_PROTOCOL_VERSION);
            return attributes;
          });
    }

    optionsBuilder.setClientSpanErrorMapper(
        (uri, throwable) -> {
          if (!throwable.getClass().getName().endsWith("WebClientRequestException")) {
            String uriString = uri.toString();
            if (uriString.equals("http://localhost:61/")) { // unopened port
              if (!throwable.getClass().getName().endsWith("AnnotatedConnectException")) {
                throwable = throwable.getCause();
              }
            } else if (uriString.equals("https://192.0.2.1/")) { // non routable address
              throwable = throwable.getCause();
            }
          }
          return throwable;
        });

    optionsBuilder.setSingleConnectionFactory(
        (host, port) -> new SpringWebfluxSingleConnection(host, port, this::instrument));
  }

  private static final MethodHandle GET_STATUS_CODE;
  private static final MethodHandle STATUS_CODE_VALUE;

  static {
    MethodHandle getStatusCode;
    MethodHandle statusCodeValue;
    Class<?> httpStatusCodeClass;

    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    try {
      httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
    } catch (ClassNotFoundException e) {
      try {
        httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatus");
      } catch (ClassNotFoundException e2) {
        throw new LinkageError("Did not find neither HttpStatus nor HttpStatusCode class", e2);
      }
    }

    try {
      getStatusCode =
          lookup.findVirtual(
              ClientResponse.class, "statusCode", MethodType.methodType(httpStatusCodeClass));
      statusCodeValue =
          lookup.findVirtual(httpStatusCodeClass, "value", MethodType.methodType(int.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new LinkageError("Did not find statusCode() method", e);
    }

    GET_STATUS_CODE = getStatusCode;
    STATUS_CODE_VALUE = statusCodeValue;
  }

  private static int getStatusCode(ClientResponse response) {
    try {
      Object statusCode = GET_STATUS_CODE.invoke(response);
      return (int) STATUS_CODE_VALUE.invoke(statusCode);
    } catch (Throwable e) {
      throw new AssertionError(e);
    }
  }
}
