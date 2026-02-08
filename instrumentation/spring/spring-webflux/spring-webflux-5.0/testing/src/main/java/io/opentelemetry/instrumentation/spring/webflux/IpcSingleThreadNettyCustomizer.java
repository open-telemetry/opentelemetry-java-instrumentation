/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux;

import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import reactor.ipc.netty.http.server.HttpServerOptions;
import reactor.ipc.netty.resources.LoopResources;

public class IpcSingleThreadNettyCustomizer implements NettyServerCustomizer {

  @Override
  public void customize(HttpServerOptions.Builder builder) {
    builder.loopResources(LoopResources.create("my-http", 1, true));
  }
}
