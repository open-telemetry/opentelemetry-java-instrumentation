/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RpcSpanNameExtractorTest {

  @ParameterizedTest
  @ArgumentsSource(Args.class)
  void normal(
      RpcCommonAttributesGetter<RpcRequest> getter, SpanNameExtractor<RpcRequest> extractor) {
    RpcRequest request = new RpcRequest();

    when(getter.service(request)).thenReturn("my.Service");
    when(getter.method(request)).thenReturn("Method");

    assertThat(extractor.extract(request)).isEqualTo("my.Service/Method");
  }

  @ParameterizedTest
  @ArgumentsSource(Args.class)
  void serviceNull(
      RpcCommonAttributesGetter<RpcRequest> getter, SpanNameExtractor<RpcRequest> extractor) {
    RpcRequest request = new RpcRequest();

    when(getter.method(request)).thenReturn("Method");

    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  @ParameterizedTest
  @ArgumentsSource(Args.class)
  void methodNull(
      RpcCommonAttributesGetter<RpcRequest> getter, SpanNameExtractor<RpcRequest> extractor) {
    RpcRequest request = new RpcRequest();

    when(getter.service(request)).thenReturn("my.Service");

    assertThat(extractor.extract(request)).isEqualTo("RPC request");
  }

  static final class Args implements ArgumentsProvider {

    @SuppressWarnings("unchecked")
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      RpcServerAttributesGetter<RpcRequest> serverGetter = mock(RpcServerAttributesGetter.class);
      RpcClientAttributesGetter<RpcRequest> clientGetter = mock(RpcClientAttributesGetter.class);
      return Stream.of(
          Arguments.of(serverGetter, RpcSpanNameExtractor.create(serverGetter)),
          Arguments.of(clientGetter, RpcSpanNameExtractor.create(clientGetter)));
    }
  }

  static class RpcRequest {}
}
