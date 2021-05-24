/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RpcSpanNameExtractorTest {

  @Mock RpcAttributesExtractor<RpcRequest, Void> attributesExtractor;

  @Test
  void normal() {
    RpcRequest request = new RpcRequest();

    when(attributesExtractor.service(request)).thenReturn("my.Service");
    when(attributesExtractor.method(request)).thenReturn("Method");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(attributesExtractor);
    assertThat(extractor.extract(request)).isEqualTo("my.Service/Method");
  }

  @Test
  void serviceNull() {
    RpcRequest request = new RpcRequest();

    when(attributesExtractor.method(request)).thenReturn("Method");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(attributesExtractor);
    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  @Test
  void methodNull() {
    RpcRequest request = new RpcRequest();

    when(attributesExtractor.service(request)).thenReturn("my.Service");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(attributesExtractor);
    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  static class RpcRequest {}
}
