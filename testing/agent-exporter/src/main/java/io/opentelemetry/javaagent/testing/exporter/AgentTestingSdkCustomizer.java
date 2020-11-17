package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.sdk.trace.TracerSdkManagement;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

@AutoService(TracerCustomizer.class)
public class AgentTestingSdkCustomizer implements TracerCustomizer {
  @Override
  public void configure(TracerSdkManagement tracerManagement) {
    tracerManagement.addSpanProcessor(
        SimpleSpanProcessor.builder(AgentTestingExporterFactory.exporter).build());
  }
}
