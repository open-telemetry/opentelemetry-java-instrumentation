/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientConfigBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("rawtypes")
public final class HttpClientInstrumenterFactory {

  private HttpClientInstrumenterFactory() {}

  public static <REQUEST, RESPONSE>
      HttpClientInstrumenterFactory.Builder<REQUEST, RESPONSE> builder(
          HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    return configureBuilder(
        CommonConfig.get(), new Builder<>(GlobalOpenTelemetry.get(), httpAttributesGetter));
  }

  @SuppressWarnings("unchecked")
  @CanIgnoreReturnValue
  public static <T extends HttpClientConfigBuilder> T configureBuilder(
      CommonConfig commonConfig, T builder) {
    CommonConfigSetter.set(commonConfig::getKnownHttpRequestMethods, builder::setKnownMethods);
    CommonConfigSetter.set(
        commonConfig::getClientRequestHeaders, builder::setCapturedRequestHeaders);
    CommonConfigSetter.set(
        commonConfig::getClientResponseHeaders, builder::setCapturedResponseHeaders);
    CommonConfigSetter.set(commonConfig::getPeerServiceResolver, builder::setPeerServiceResolver);
    CommonConfigSetter.set(
        commonConfig::shouldEmitExperimentalHttpClientTelemetry,
        builder::setEmitExperimentalHttpClientMetrics);
    return builder;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Builder<REQUEST, RESPONSE>
      extends HttpClientConfigBuilder<HttpClientConfigBuilder, REQUEST, RESPONSE> {
    Builder(
        OpenTelemetry openTelemetry,
        HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
      super(openTelemetry, httpAttributesGetter);
    }

    @Override
    public Instrumenter<REQUEST, RESPONSE> build(
        String instrumentationName, TextMapSetter<REQUEST> setter) {
      return super.build(instrumentationName, setter);
    }

    @Override
    public Instrumenter<REQUEST, RESPONSE> buildWithManualInjection(String instrumentationName) {
      return super.buildWithManualInjection(instrumentationName);
    }
  }
}
