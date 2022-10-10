/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientTracingHandler
    extends CombinedChannelDuplexHandler<
        HttpClientResponseTracingHandler, HttpClientRequestTracingHandler> {

  public HttpClientTracingHandler(Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter) {
    super(
        new HttpClientResponseTracingHandler(instrumenter),
        new HttpClientRequestTracingHandler(instrumenter));
  }
}
