/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.lenient;
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
  @SuppressWarnings("deprecation") // testing deprecated method
  void normal() {
    RpcRequest request = new RpcRequest();

    if (emitPreviewRpcSemconv()) {
      lenient().when(getter.getRpcSystemName(request)).thenReturn("custom_rpc");
      when(getter.getRpcMethod(request)).thenReturn("my.Service/Method");
    } else {
      when(getter.getService(request)).thenReturn("my.Service");
      when(getter.getMethod(request)).thenReturn("Method");
    }

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("my.Service/Method");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void serviceNull() {
    assumeTrue(!emitPreviewRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getMethod(request)).thenReturn("Method");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  @Test
  void methodNull() {
    assumeTrue(!emitPreviewRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getService(request)).thenReturn("my.Service");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  @Test
  void rpcMethodNull_fallsBackToSystemName() {
    assumeTrue(emitPreviewRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getRpcSystemName(request)).thenReturn("custom_rpc");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("custom_rpc");
  }

  @Test
  void rpcMethodOther_fallsBackToSystemName() {
    assumeTrue(emitPreviewRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getRpcMethod(request)).thenReturn("_OTHER");
    when(getter.getRpcSystemName(request)).thenReturn("custom_rpc");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("custom_rpc");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method fallback
  void rpcMethodNull_fallsBackToSystemName_viaGetSystem() {
    assumeTrue(emitPreviewRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getRpcSystemName(request)).thenCallRealMethod();
    when(getter.getSystem(request)).thenReturn("custom_rpc");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("custom_rpc");
  }

  static class RpcRequest {}
}
