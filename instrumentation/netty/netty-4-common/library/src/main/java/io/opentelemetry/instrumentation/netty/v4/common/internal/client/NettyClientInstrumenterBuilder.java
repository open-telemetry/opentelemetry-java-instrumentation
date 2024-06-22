/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.util.Optional;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class NettyClientInstrumenterBuilder
    extends DefaultHttpClientInstrumenterBuilder<HttpRequestAndChannel, HttpResponse> {
  private PeerServiceResolver peerServiceResolver;

  public NettyClientInstrumenterBuilder(String instrumentationName, OpenTelemetry openTelemetry) {
    super(
        instrumentationName,
        openTelemetry,
        new NettyHttpClientAttributesGetter(),
        Optional.of(HttpRequestHeadersSetter.INSTANCE));
  }

  @CanIgnoreReturnValue
  @Override
  public NettyClientInstrumenterBuilder setPeerServiceResolver(
      PeerServiceResolver peerServiceResolver) {
    this.peerServiceResolver = peerServiceResolver;
    super.setPeerServiceResolver(peerServiceResolver);
    return this;
  }

  public PeerServiceResolver getPeerServiceResolver() {
    return peerServiceResolver;
  }
}
