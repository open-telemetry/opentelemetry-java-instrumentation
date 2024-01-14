/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package reactor.netty.http.client;

import java.util.function.Function;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

// class in reactor package to access package-private code
public final class HttpClientConfigBuddy {

  public static boolean hasDeferredConfig(HttpClientConfig config) {
    return config.deferredConf != null;
  }

  public static Function<? super Mono<? extends Connection>, ? extends Mono<? extends Connection>>
      getConnector(HttpClientConfig config) {
    return config.connector == null ? Function.identity() : config.connector;
  }

  private HttpClientConfigBuddy() {}
}
