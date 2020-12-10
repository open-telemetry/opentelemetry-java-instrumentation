package com.example.javaagent;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.TracerSdkManagement;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * This is the main entry point for the majority of Instrumentation Agent's customizations.
 * It allows for configuring various aspects of OpenTelemetrySdk.
 * See the {@link #configure(TracerSdkManagement)} method below.
 *
 * Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 */
public class DemoTracerCustomizer implements TracerCustomizer {
  @Override
  public void configure(TracerSdkManagement ignore) {
    OpenTelemetrySdk.Builder sdkBuilder = OpenTelemetrySdk.builder();

    sdkBuilder.addSpanProcessor(new DemoSpanProcessor());
    sdkBuilder.addSpanProcessor(SimpleSpanProcessor.builder(new DemoSpanExporter()).build());

    TraceConfig currentConfig = TraceConfig.getDefault();
    TraceConfig newConfig = currentConfig.toBuilder()
        .setSampler(new DemoSampler())
        .setMaxLengthOfAttributeValues(128)
        .build();
    sdkBuilder.setTraceConfig(newConfig);

    sdkBuilder.setIdGenerator(new DemoIdGenerator());
    sdkBuilder.setPropagators(ContextPropagators.create(new DemoPropagator()));

    OpenTelemetry.set(sdkBuilder.build());
  }

}
