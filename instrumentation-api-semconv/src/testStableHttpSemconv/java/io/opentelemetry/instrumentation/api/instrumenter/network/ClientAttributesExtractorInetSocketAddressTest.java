/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class ClientAttributesExtractorInetSocketAddressTest {

  static class TestClientAttributesGetter
      implements ClientAttributesGetter<InetSocketAddress, Void> {

    @Nullable
    @Override
    public String getClientAddress(InetSocketAddress request) {
      // covered in ClientAttributesExtractorTest
      return null;
    }

    @Nullable
    @Override
    public Integer getClientPort(InetSocketAddress request) {
      // covered in ClientAttributesExtractorTest
      return null;
    }

    @Nullable
    @Override
    public InetSocketAddress getClientInetSocketAddress(
        InetSocketAddress request, @Nullable Void response) {
      return request;
    }
  }

  @Test
  @SuppressWarnings("AddressSelection")
  void fullAddress() {
    InetSocketAddress address = new InetSocketAddress("api.github.com", 456);
    assertThat(address.getAddress().getHostAddress()).isNotNull();

    AttributesExtractor<InetSocketAddress, Void> extractor =
        ClientAttributesExtractor.create(new TestClientAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), address);
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), address, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.CLIENT_SOCKET_ADDRESS, address.getAddress().getHostAddress()),
            entry(SemanticAttributes.CLIENT_SOCKET_PORT, 456L));
  }

  @Test
  void noAttributes() {
    AttributesExtractor<InetSocketAddress, Void> extractor =
        ClientAttributesExtractor.create(new TestClientAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), null);
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), null, null, null);
    assertThat(endAttributes.build()).isEmpty();
  }
}
