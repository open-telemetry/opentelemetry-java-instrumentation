/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.CombinedSimpleChannelHandler;

public class HttpClientTracingHandler
    extends CombinedSimpleChannelHandler<
        HttpClientResponseTracingHandler, HttpClientRequestTracingHandler> {

  public HttpClientTracingHandler() {
    super(new HttpClientResponseTracingHandler(), new HttpClientRequestTracingHandler());
  }
}
