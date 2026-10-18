/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;

class DubboTelemetryBuilderTest {

  @Test
  void wrappedSpanNameExtractorUsesBuilderOpenTelemetry() {
    boolean ownerUsesPreview = !emitPreviewRpcSemconv();
    OpenTelemetry openTelemetry = openTelemetry(ownerUsesPreview);
    AtomicReference<SpanNameExtractor<DubboRequest>> wrappedExtractor = new AtomicReference<>();
    DubboTelemetry.builder(openTelemetry)
        .setClientSpanNameExtractorCustomizer(
            extractor -> {
              SpanNameExtractor<DubboRequest> wrapper =
                  request -> extractor.extract(request);
              wrappedExtractor.set(wrapper);
              return wrapper;
            })
        .build();

    DubboRequest request =
        DubboRequest.createForUnknownService(
            mock(RpcInvocation.class), "org.example.Service/hello", null);
    assertThat(wrappedExtractor.get().extract(request))
        .isEqualTo(ownerUsesPreview ? "_OTHER" : "RPC request");
  }

  private static OpenTelemetry openTelemetry(boolean ownerUsesPreview) {
    DeclarativeConfigProperties general =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties rpcSemconv = general.get("rpc").get("semconv");
    when(rpcSemconv.getInt("version")).thenReturn(ownerUsesPreview ? 1 : 0);
    when(rpcSemconv.getBoolean("experimental", false)).thenReturn(ownerUsesPreview);

    DeclarativeConfigProperties common =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    when(configProvider.getGeneralInstrumentationConfig()).thenReturn(general);
    when(configProvider.getInstrumentationConfig("common")).thenReturn(common);
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    OpenTelemetry noop = OpenTelemetry.noop();
    when(openTelemetry.getTracerProvider()).thenReturn(noop.getTracerProvider());
    when(openTelemetry.getMeterProvider()).thenReturn(noop.getMeterProvider());
    when(openTelemetry.getLogsBridge()).thenReturn(noop.getLogsBridge());
    when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
    when(openTelemetry.getGeneralInstrumentationConfig()).thenReturn(general);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(common);
    return openTelemetry;
  }
}
