/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.server;

import static io.opentelemetry.instrumentation.spring.webflux.server.AbstractSpringWebFluxServerTest.NESTED_PATH;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import reactor.core.publisher.Mono;

@SuppressWarnings("IdentifierName") // method names are snake_case to match endpoints
public abstract class ServerTestController {

  // Spring 5.x uses setStatusCode(HttpStatus), Spring 6+ uses setStatusCode(HttpStatusCode)
  private static final Method setStatusCodeMethod;

  static {
    Method method;
    try {
      // Try Spring 6+ signature first (HttpStatusCode interface)
      Class<?> httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
      method = ServerHttpResponse.class.getMethod("setStatusCode", httpStatusCodeClass);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      // Fall back to Spring 5.x signature (HttpStatus enum)
      try {
        method = ServerHttpResponse.class.getMethod("setStatusCode", HttpStatus.class);
      } catch (NoSuchMethodException ex) {
        throw new IllegalStateException(ex);
      }
    }
    setStatusCodeMethod = method;
  }

  @GetMapping("/success")
  public Mono<String> success(ServerHttpResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.SUCCESS;

    return wrapControllerMethod(
        endpoint,
        () -> {
          setStatus(response, endpoint);
          return endpoint.getBody();
        });
  }

  @GetMapping("/query")
  public Mono<String> query_param(ServerHttpRequest request, ServerHttpResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.QUERY_PARAM;

    return wrapControllerMethod(
        endpoint,
        () -> {
          setStatus(response, endpoint);
          return request.getURI().getRawQuery();
        });
  }

  @GetMapping("/redirect")
  public Mono<String> redirect(ServerHttpResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.REDIRECT;

    return wrapControllerMethod(
        endpoint,
        () -> {
          setStatus(response, endpoint);
          response.getHeaders().setLocation(URI.create(endpoint.getBody()));
          return "";
        });
  }

  @GetMapping("/error-status")
  Mono<String> error(ServerHttpResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.ERROR;

    return wrapControllerMethod(
        endpoint,
        () -> {
          setStatus(response, endpoint);
          return endpoint.getBody();
        });
  }

  @GetMapping("/exception")
  Mono<Void> exception() {
    ServerEndpoint endpoint = ServerEndpoint.EXCEPTION;

    return wrapControllerMethod(
        endpoint,
        () -> {
          throw new IllegalStateException(endpoint.getBody());
        });
  }

  @GetMapping("/path/{id}/param")
  Mono<String> path_param(ServerHttpResponse response, @PathVariable("id") String id) {
    ServerEndpoint endpoint = ServerEndpoint.PATH_PARAM;

    return wrapControllerMethod(
        endpoint,
        () -> {
          setStatus(response, endpoint);
          return id;
        });
  }

  @GetMapping("/child")
  Mono<String> indexed_child(ServerHttpRequest request, ServerHttpResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.INDEXED_CHILD;

    return wrapControllerMethod(
        endpoint,
        () -> {
          endpoint.collectSpanAttributes(it -> request.getQueryParams().getFirst(it));
          setStatus(response, endpoint);
          return endpoint.getBody();
        });
  }

  @GetMapping("/captureHeaders")
  public Mono<String> capture_headers(ServerHttpRequest request, ServerHttpResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.CAPTURE_HEADERS;

    return wrapControllerMethod(
        endpoint,
        () -> {
          setStatus(response, endpoint);
          response
              .getHeaders()
              .set("X-Test-Response", request.getHeaders().getFirst("X-Test-Request"));
          return endpoint.getBody();
        });
  }

  @GetMapping("/nestedPath")
  public Mono<String> nested_path(ServerHttpRequest request, ServerHttpResponse response) {
    ServerEndpoint endpoint = NESTED_PATH;

    return wrapControllerMethod(
        endpoint,
        () -> {
          setStatus(response, endpoint);
          return endpoint.getBody();
        });
  }

  protected abstract <T> Mono<T> wrapControllerMethod(ServerEndpoint endpoint, Supplier<T> handler);

  protected void setStatus(ServerHttpResponse response, ServerEndpoint endpoint) {
    HttpStatus status = HttpStatus.resolve(endpoint.getStatus());
    try {
      setStatusCodeMethod.invoke(response, status);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set status code", e);
    }
  }
}
