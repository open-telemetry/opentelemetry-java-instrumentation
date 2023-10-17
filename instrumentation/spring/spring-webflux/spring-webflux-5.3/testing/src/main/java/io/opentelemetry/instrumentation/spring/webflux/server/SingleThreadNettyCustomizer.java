/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.server;

import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import reactor.netty.http.server.HttpServer;

public class SingleThreadNettyCustomizer implements NettyServerCustomizer {

  @Override
  public HttpServer apply(HttpServer server) {
    return server.runOn(reactor.netty.resources.LoopResources.create("my-http", 1, true));
  }
}
