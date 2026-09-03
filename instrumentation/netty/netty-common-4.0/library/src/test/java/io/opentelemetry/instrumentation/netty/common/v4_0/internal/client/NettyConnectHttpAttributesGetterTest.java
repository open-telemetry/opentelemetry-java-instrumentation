/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.client;

import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.netty.channel.Channel;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class NettyConnectHttpAttributesGetterTest {

  @Test
  void shouldUseOtherForUnavailableMethod() {
    NettyConnectionRequest request =
        NettyConnectionRequest.connect(InetSocketAddress.createUnresolved("example.com", 1234));
    AttributesExtractor<NettyConnectionRequest, Channel> extractor =
        HttpClientAttributesExtractor.create(new NettyConnectHttpAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build().asMap())
        .containsOnly(
            entry(HTTP_REQUEST_METHOD, _OTHER),
            entry(SERVER_ADDRESS, "example.com"),
            entry(SERVER_PORT, 1234L));
  }
}
