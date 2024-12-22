/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import reactor.ipc.netty.http.server.HttpServerOptions;

public class IpcSingleThreadNettyCustomizer implements NettyServerCustomizer {

  @Override
  public void customize(HttpServerOptions.Builder builder) {
    builder.loopResources(reactor.ipc.netty.resources.LoopResources.create("my-http", 1, true));
  }
}
