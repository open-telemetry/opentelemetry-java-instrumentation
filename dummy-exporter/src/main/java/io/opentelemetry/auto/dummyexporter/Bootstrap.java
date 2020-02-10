package io.opentelemetry.auto.dummyexporter;

import io.opentelemetry.auto.exportersupport.ExporterFactory;

public class Bootstrap {
  public static ExporterFactory getFactory() {
    return new DummyExporterFactory();
  }
}
