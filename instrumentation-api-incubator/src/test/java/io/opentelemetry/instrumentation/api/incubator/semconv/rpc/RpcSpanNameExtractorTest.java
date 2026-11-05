/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RpcSpanNameExtractorTest {

  @Mock RpcAttributesGetter<RpcRequest, Object> getter;

  @Test
  void normal() {
    RpcRequest request = new RpcRequest();

    when(getter.getRpcMethod(request)).thenReturn("my.Service/Method");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("my.Service/Method");
  }

  @Test
  void rpcMethodNull_fallsBackToSystemName() {
    RpcRequest request = new RpcRequest();

    when(getter.getRpcSystemName(request)).thenReturn("custom_rpc");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("custom_rpc");
  }

  @Test
  void rpcMethodOther_fallsBackToSystemName() {
    RpcRequest request = new RpcRequest();

    when(getter.getRpcMethod(request)).thenReturn("_OTHER");
    when(getter.getRpcSystemName(request)).thenReturn("custom_rpc");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("custom_rpc");
  }

  static class RpcRequest {}
}
