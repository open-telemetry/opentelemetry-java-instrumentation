package io.opentelemetry.auto.tooling.exporter;

import java.util.HashMap;
import java.util.Map;

public class ExporterRegistry {
  private static final ExporterRegistry instance = new ExporterRegistry();

  private final Map<String, SpanExporterFactory> factories = new HashMap<>();

  public static ExporterRegistry getInstance() {
    return instance;
  }

  public ExporterRegistry() {
    registerDefaultFactories();
  }

  public void registerFactory(final String name, final SpanExporterFactory factory) {
    factories.put(name, factory);
  }

  public SpanExporterFactory getFactory(final String name) throws ExporterConfigException {
    final SpanExporterFactory f = factories.get(name);
    if (f == null) {
      throw new ExporterConfigException("Exporter type " + name + " is not registered");
    }
    return f;
  }

  private void registerDefaultFactories() {
    registerFactory("jaeger", new JaegerExporterFactory());
  }
}
