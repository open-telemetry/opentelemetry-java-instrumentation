package com.example.javaagent;

import io.opentelemetry.sdk.autoconfigure.spi.SdkTracerProviderConfigurer;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * This is the main entry point for the majority of Instrumentation Agent's customizations.
 * It allows for configuring various aspects of OpenTelemetrySdk.
 * See the {@link #configure(SdkTracerProviderBuilder)} method below.
 * <p>
 * Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 */
public class DemoTracerCustomizer implements SdkTracerProviderConfigurer {
  @Override
  public void configure(SdkTracerProviderBuilder tracerProvider) {
    tracerProvider
        .setIdGenerator(new DemoIdGenerator())
        .setTraceConfig(TraceConfig.builder()
            .setSampler(new DemoSampler())
            .setMaxLengthOfAttributeValues(128)
            .build())
        .addSpanProcessor(new DemoSpanProcessor())
        .addSpanProcessor(SimpleSpanProcessor.create(new DemoSpanExporter()));
  }
}
