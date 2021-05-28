/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import io.netty.bootstrap.Bootstrap;
import io.opentelemetry.context.Context;
import java.util.function.BiFunction;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

public class MapConnect
    implements BiFunction<Mono<? extends Connection>, Bootstrap, Mono<? extends Connection>> {

  static final String CONTEXT_ATTRIBUTE = MapConnect.class.getName() + ".Context";

  @Override
  public Mono<? extends Connection> apply(Mono<? extends Connection> m, Bootstrap b) {
    return m.subscriberContext(s -> s.put(CONTEXT_ATTRIBUTE, Context.current()));
  }
}
