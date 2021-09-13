package com.example.javaagent;

import io.opentelemetry.sdk.autoconfigure.spi.SdkTracerProviderConfigurer;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * This is one of the main entry points for Instrumentation Agent's customizations. It allows
 * configuring {@link SdkTracerProviderBuilder}. See the {@link
 * #configure(SdkTracerProviderBuilder)} method below.
 *
 * <p>Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 *
 * @see SdkTracerProviderConfigurer
 * @see DemoPropagatorProvider
 */
public class DemoSdkTracerProviderConfigurer implements SdkTracerProviderConfigurer {
  @Override
  public void configure(SdkTracerProviderBuilder tracerProvider) {
    tracerProvider
        .setIdGenerator(new DemoIdGenerator())
        .setSpanLimits(SpanLimits.builder().setMaxNumberOfAttributes(1024).build())
        .setSampler(new DemoSampler())
        .addSpanProcessor(new DemoSpanProcessor())
        .addSpanProcessor(SimpleSpanProcessor.create(new DemoSpanExporter()));
  }
}
