/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
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

    if (SemconvStability.emitStableRpcSemconv()) {
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
    assumeTrue(!SemconvStability.emitStableRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getMethod(request)).thenReturn("Method");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  @Test
  void methodNull() {
    assumeTrue(!SemconvStability.emitStableRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getService(request)).thenReturn("my.Service");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  @Test
  void rpcMethodSystemFallback() {
    assumeTrue(SemconvStability.emitStableRpcSemconv());

    RpcRequest request = new RpcRequest();

    when(getter.getSystem(request)).thenReturn("system");

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("system");
  }

  @Test
  void rpcMethodNull() {
    assumeTrue(SemconvStability.emitStableRpcSemconv());

    RpcRequest request = new RpcRequest();

    SpanNameExtractor<RpcRequest> extractor = RpcSpanNameExtractor.create(getter);
    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  static class RpcRequest {}
}
