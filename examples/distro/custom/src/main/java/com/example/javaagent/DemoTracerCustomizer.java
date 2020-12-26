package com.example.javaagent;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.trace.SdkTracerManagement;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * This is the main entry point for the majority of Instrumentation Agent's customizations.
 * It allows for configuring various aspects of OpenTelemetrySdk.
 * See the {@link #configure(SdkTracerManagement)} method below.
 * <p>
 * Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 */
public class DemoTracerCustomizer implements TracerCustomizer {
  @Override
  public void configure(SdkTracerManagement tracerManagement) {
    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .setIdGenerator(new DemoIdGenerator())
        .setTraceConfig(TraceConfig.getDefault().toBuilder()
            .setSampler(new DemoSampler())
            .setMaxLengthOfAttributeValues(128)
            .build())
        .build();

    sdkTracerProvider.addSpanProcessor(new DemoSpanProcessor());
    sdkTracerProvider.addSpanProcessor(SimpleSpanProcessor.builder(new DemoSpanExporter()).build());

    OpenTelemetrySdkBuilder sdkBuilder = OpenTelemetrySdk.builder()
        .setPropagators(ContextPropagators.create(new DemoPropagator()))
        .setTracerProvider(sdkTracerProvider);
    GlobalOpenTelemetry.set(sdkBuilder.build());
  }
}
