/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory;
import io.opentelemetry.javaagent.spi.exporter.MetricServer;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.TracerSdkManagement;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracerInstaller {
  private static final Logger log = LoggerFactory.getLogger(TracerInstaller.class);

  private static final String EXPORTER_JAR_CONFIG = "otel.exporter.jar";
  private static final String EXPORTERS_CONFIG = "otel.exporter";
  private static final String PROPAGATORS_CONFIG = "otel.propagators";
  private static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";
  private static final List<String> DEFAULT_EXPORTERS = Collections.singletonList("otlp");

  /** Register agent tracer if no agent tracer is already registered. */
  @SuppressWarnings("unused")
  public static synchronized void installAgentTracer() {
    if (Config.get().getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true)) {
      Properties config = Config.get().asJavaProperties();

      configure(config);
      // Try to create an exporter from external jar file
      String exporterJar = Config.get().getProperty(EXPORTER_JAR_CONFIG);
      if (exporterJar != null) {
        installExportersFromJar(exporterJar, config);
      } else {
        // Try to create embedded exporter
        installExporters(Config.get().getListProperty(EXPORTERS_CONFIG, DEFAULT_EXPORTERS), config);
      }
    } else {
      log.info("Tracing is disabled.");
    }

    PropagatorsInitializer.initializePropagators(Config.get().getListProperty(PROPAGATORS_CONFIG));
  }

  private static synchronized void installExporters(List<String> exporters, Properties config) {
    for (String exporterName : exporters) {
      SpanExporterFactory spanExporterFactory = findSpanExporterFactory(exporterName);
      if (spanExporterFactory != null) {
        installExporter(spanExporterFactory, config);
      } else {
        log.warn("No {} span exporter found", exporterName);
      }

      MetricExporterFactory metricExporterFactory = findMetricExporterFactory(exporterName);
      if (metricExporterFactory != null) {
        installExporter(metricExporterFactory, config);
      } else {
        log.debug("No {} metric exporter found", exporterName);
      }

      MetricServer metricServer = findMetricServer(exporterName);
      if (metricServer != null) {
        installMetricServer(metricServer, config);
      } else {
        log.debug("No {} metric server found", exporterName);
      }
    }
  }

  private static MetricExporterFactory findMetricExporterFactory(String exporterName) {
    ServiceLoader<MetricExporterFactory> serviceLoader =
        ServiceLoader.load(MetricExporterFactory.class, TracerInstaller.class.getClassLoader());

    for (MetricExporterFactory metricExporterFactory : serviceLoader) {
      if (metricExporterFactory.getNames().contains(exporterName)) {
        return metricExporterFactory;
      }
    }
    return null;
  }

  private static MetricServer findMetricServer(String exporterName) {
    ServiceLoader<MetricServer> serviceLoader =
        ServiceLoader.load(MetricServer.class, TracerInstaller.class.getClassLoader());

    for (MetricServer metricServer : serviceLoader) {
      if (metricServer.getNames().contains(exporterName)) {
        return metricServer;
      }
    }
    return null;
  }

  private static SpanExporterFactory findSpanExporterFactory(String exporterName) {
    ServiceLoader<SpanExporterFactory> serviceLoader =
        ServiceLoader.load(SpanExporterFactory.class, TracerInstaller.class.getClassLoader());

    for (SpanExporterFactory spanExporterFactory : serviceLoader) {
      if (spanExporterFactory.getNames().contains(exporterName)) {
        return spanExporterFactory;
      }
    }
    return null;
  }

  private static synchronized void installExportersFromJar(String exporterJar, Properties config) {
    URL url;
    try {
      url = new File(exporterJar).toURI().toURL();
    } catch (MalformedURLException e) {
      log.warn("Filename could not be parsed: " + exporterJar + ". Exporter is not installed");
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
      return;
    }
    ExporterClassLoader exporterLoader =
        new ExporterClassLoader(url, TracerInstaller.class.getClassLoader());

    SpanExporterFactory spanExporterFactory =
        getExporterFactory(SpanExporterFactory.class, exporterLoader);

    if (spanExporterFactory != null) {
      installExporter(spanExporterFactory, config);
    } else {
      log.warn("No span exporter found in {}", exporterJar);
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
    }

    MetricExporterFactory metricExporterFactory =
        getExporterFactory(MetricExporterFactory.class, exporterLoader);
    if (metricExporterFactory != null) {
      installExporter(metricExporterFactory, config);
    }
  }

  private static void installExporter(
      MetricExporterFactory metricExporterFactory, Properties config) {
    MetricExporter metricExporter = metricExporterFactory.fromConfig(config);
    IntervalMetricReader.builder()
        .readProperties(config)
        .setMetricExporter(metricExporter)
        .setMetricProducers(
            Collections.singleton(OpenTelemetrySdk.getGlobalMeterProvider().getMetricProducer()))
        .build();
    log.info("Installed metric exporter: " + metricExporter.getClass().getName());
  }

  private static void installExporter(SpanExporterFactory spanExporterFactory, Properties config) {
    SpanExporter spanExporter = spanExporterFactory.fromConfig(config);
    SpanProcessor spanProcessor =
        BatchSpanProcessor.builder(spanExporter).readProperties(config).build();
    OpenTelemetrySdk.getGlobalTracerManagement().addSpanProcessor(spanProcessor);
    log.info("Installed span exporter: " + spanExporter.getClass().getName());
  }

  private static void installMetricServer(MetricServer metricServer, Properties config) {
    MetricProducer metricProducer = OpenTelemetrySdk.getGlobalMeterProvider().getMetricProducer();
    metricServer.start(metricProducer, config);
    log.info("Installed metric server: " + metricServer.getClass().getName());
  }

  private static <F> F getExporterFactory(Class<F> service, ExporterClassLoader exporterLoader) {
    ServiceLoader<F> serviceLoader = ServiceLoader.load(service, exporterLoader);
    Iterator<F> i = serviceLoader.iterator();
    if (i.hasNext()) {
      F factory = i.next();
      if (i.hasNext()) {
        log.warn(
            "Exporter JAR defines more than one {}. Only the first one found will be used",
            service.getName());
      }
      return factory;
    }
    return null;
  }

  private static void configure(Properties config) {
    TracerSdkManagement tracerManagement = OpenTelemetrySdk.getGlobalTracerManagement();

    // Register additional thread details logging span processor
    tracerManagement.addSpanProcessor(new AddThreadDetailsSpanProcessor());

    // Execute any user-provided (usually vendor-provided) configuration logic.
    ServiceLoader<TracerCustomizer> serviceLoader =
        ServiceLoader.load(TracerCustomizer.class, TracerInstaller.class.getClassLoader());
    for (TracerCustomizer customizer : serviceLoader) {
      customizer.configure(tracerManagement);
    }

    /* Update trace config from env vars or sys props */
    TraceConfig activeTraceConfig = tracerManagement.getActiveTraceConfig();
    tracerManagement.updateActiveTraceConfig(
        activeTraceConfig.toBuilder().readProperties(config).build());
  }

  @SuppressWarnings("unused")
  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
