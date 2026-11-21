/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

class PetclinicStartupUptimeResponseAdviceTest {
  private static final String STARTUP_UPTIME_HEADER = "X-Startup-JVM-Uptime-Millis";

  @Test
  void addsUptimeOnlyToSeededOwnerGetAndPreservesBody() {
    PetclinicStartupUptimeResponseAdvice advice = new PetclinicStartupUptimeResponseAdvice();
    Object body = new Object();
    HttpHeaders headers = new HttpHeaders();

    Object result =
        advice.beforeBodyWrite(
            body,
            null,
            null,
            null,
            request(HttpMethod.GET, "/petclinic/api/owners/1"),
            response(headers));

    assertThat(result).isSameAs(body);
    assertThat(headers.getFirst(STARTUP_UPTIME_HEADER)).isNotNull();
    assertThat(Long.parseLong(headers.getFirst(STARTUP_UPTIME_HEADER))).isPositive();

    headers.clear();
    advice.beforeBodyWrite(
        body,
        null,
        null,
        null,
        request(HttpMethod.GET, "/petclinic/api/owners/2"),
        response(headers));
    assertThat(headers).doesNotContainKey(STARTUP_UPTIME_HEADER);
  }

  @Test
  void doesNotAddUptimeToOtherMethods() {
    HttpHeaders headers = new HttpHeaders();

    new PetclinicStartupUptimeResponseAdvice()
        .beforeBodyWrite(
            "owner",
            null,
            null,
            null,
            request(HttpMethod.POST, "/petclinic/api/owners/1"),
            response(headers));

    assertThat(headers).doesNotContainKey(STARTUP_UPTIME_HEADER);
  }

  private static ServerHttpRequest request(HttpMethod method, String path) {
    return proxy(
        ServerHttpRequest.class,
        (proxy, invokedMethod, arguments) -> {
          if (invokedMethod.getName().equals("getMethod")) {
            return method;
          }
          if (invokedMethod.getName().equals("getURI")) {
            return URI.create(path);
          }
          return null;
        });
  }

  private static ServerHttpResponse response(HttpHeaders headers) {
    return proxy(
        ServerHttpResponse.class,
        (proxy, invokedMethod, arguments) ->
            invokedMethod.getName().equals("getHeaders") ? headers : null);
  }

  private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }
}
