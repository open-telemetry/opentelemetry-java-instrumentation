/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import java.lang.reflect.Field;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientConfig;

public final class HttpClientConfigAccess {

  private static final Field DEFERRED_CONF_FIELD = findField("deferredConf");
  private static final Field CONNECTOR_FIELD = findField("connector");

  private static Field findField(String name) {
    try {
      Field field = HttpClientConfig.class.getDeclaredField(name);
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      return null;
    }
  }

  public static boolean hasDeferredConfig(HttpClientConfig config) {
    if (DEFERRED_CONF_FIELD == null) {
      return false;
    }
    try {
      return DEFERRED_CONF_FIELD.get(config) != null;
    } catch (Throwable t) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  public static Function<? super Mono<? extends Connection>, ? extends Mono<? extends Connection>>
      getConnector(HttpClientConfig config) {
    if (CONNECTOR_FIELD == null) {
      return Function.identity();
    }
    try {
      Function<? super Mono<? extends Connection>, ? extends Mono<? extends Connection>> connector =
          (Function<? super Mono<? extends Connection>, ? extends Mono<? extends Connection>>)
              CONNECTOR_FIELD.get(config);
      return connector == null ? Function.identity() : connector;
    } catch (Throwable t) {
      return Function.identity();
    }
  }

  private HttpClientConfigAccess() {}
}
