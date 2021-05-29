/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.context.Context;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

public class MapConnect
    implements Function<Mono<? extends Connection>, Mono<? extends Connection>> {

  static final String CONTEXT_ATTRIBUTE = MapConnect.class.getName() + ".Context";

  @Override
  public Mono<? extends Connection> apply(Mono<? extends Connection> m) {
    return m.contextWrite(s -> s.put(CONTEXT_ATTRIBUTE, Context.current()));
  }
}
