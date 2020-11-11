package io.opentelemetry.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.TracerSdkManagement;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * This is the main entry point for the majority of Instrumentation Agent's customizations.
 * It allows for configuring various aspects of OpenTelemetrySdk.
 * Just read the {@link #configure(TracerSdkManagement)} method.
 *
 * Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 */
public class DemoTracerCustomizer implements TracerCustomizer {
  @Override
  public void configure(TracerSdkManagement ignore) {
    OpenTelemetrySdk sdk = OpenTelemetrySdk.get();
    OpenTelemetrySdk.Builder sdkBuilder = sdk.toBuilder();
    TracerSdkManagement tracerSdkManagement = sdk.getTracerManagement();

    tracerSdkManagement.addSpanProcessor(new DemoSpanProcessor());
    tracerSdkManagement.addSpanProcessor(SimpleSpanProcessor.builder(new DemoSpanExporter()).build());

    TraceConfig currentConfig = tracerSdkManagement.getActiveTraceConfig();
    TraceConfig newConfig = currentConfig.toBuilder()
        .setSampler(new DemoSampler())
        .setMaxLengthOfAttributeValues(128)
        .build();
    tracerSdkManagement.updateActiveTraceConfig(newConfig);

    //TODO https://github.com/open-telemetry/opentelemetry-java/issues/2018
//    TracerSdkProvider tracerSdkProvider = TracerSdkProvider.builder().setIdsGenerator(new DemoIdGenerator()).build();
//    sdkBuilder.setTracerProvider(tracerSdkProvider);

    sdkBuilder.setPropagators(DefaultContextPropagators.builder().addTextMapPropagator(new DemoPropagator()).build());

    OpenTelemetry.set(sdkBuilder.build());
  }

}
