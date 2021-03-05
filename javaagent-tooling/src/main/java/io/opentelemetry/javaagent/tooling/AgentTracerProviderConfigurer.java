/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.autoconfigure.spi.SdkTracerProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(SdkTracerProviderConfigurer.class)
public class AgentTracerProviderConfigurer implements SdkTracerProviderConfigurer {
  private static final Logger log = LoggerFactory.getLogger(AgentTracerProviderConfigurer.class);

  static final String EXPORTER_JAR_CONFIG = "otel.javaagent.experimental.exporter.jar";

  @Override
  public void configure(SdkTracerProviderBuilder sdkTracerProviderBuilder) {
    if (!Config.get().getBooleanProperty(OpenTelemetryInstaller.JAVAAGENT_ENABLED_CONFIG, true)) {
      return;
    }

    // Register additional thread details logging span processor
    sdkTracerProviderBuilder.addSpanProcessor(new AddThreadDetailsSpanProcessor());

    maybeConfigureExporterJar(sdkTracerProviderBuilder);
    maybeEnableLoggingExporter(sdkTracerProviderBuilder);
  }

  private static void maybeEnableLoggingExporter(SdkTracerProviderBuilder builder) {
    if (Config.get().isAgentDebugEnabled()) {
      // don't install another instance if the user has already explicitly requested it.
      if (loggingExporterIsNotAlreadyConfigured()) {
        builder.addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()));
      }
    }
  }

  private static boolean loggingExporterIsNotAlreadyConfigured() {
    return !Config.get().getProperty("otel.traces.exporter", "").equalsIgnoreCase("logging");
  }

  private static void maybeConfigureExporterJar(SdkTracerProviderBuilder sdkTracerProviderBuilder) {
    String exporterJar = Config.get().getProperty(EXPORTER_JAR_CONFIG);
    if (exporterJar == null) {
      return;
    }
    Properties config = Config.get().asJavaProperties();
    installExportersFromJar(exporterJar, config, sdkTracerProviderBuilder);
  }

  private static synchronized void installExportersFromJar(
      String exporterJar, Properties config, SdkTracerProviderBuilder builder) {
    URL url;
    try {
      url = new File(exporterJar).toURI().toURL();
    } catch (MalformedURLException e) {
      log.warn("Filename could not be parsed: " + exporterJar + ". Exporter is not installed");
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
      return;
    }
    ExporterClassLoader exporterLoader =
        new ExporterClassLoader(url, OpenTelemetryInstaller.class.getClassLoader());

    SpanExporterFactory spanExporterFactory =
        getExporterFactory(SpanExporterFactory.class, exporterLoader);

    if (spanExporterFactory != null) {
      installSpanExporter(spanExporterFactory, config, builder);
    } else {
      log.warn("No span exporter found in {}", exporterJar);
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
    }

    MetricExporterFactory metricExporterFactory =
        getExporterFactory(MetricExporterFactory.class, exporterLoader);
    if (metricExporterFactory != null) {
      installMetricExporter(metricExporterFactory, config);
    }
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

  private static void installSpanExporter(
      SpanExporterFactory spanExporterFactory,
      Properties config,
      SdkTracerProviderBuilder builder) {
    SpanExporter spanExporter = spanExporterFactory.fromConfig(config);
    SpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter).build();
    builder.addSpanProcessor(spanProcessor);
    log.info("Installed span exporter: " + spanExporter.getClass().getName());
  }

  private static void installMetricExporter(
      MetricExporterFactory metricExporterFactory, Properties config) {
    MetricExporter metricExporter = metricExporterFactory.fromConfig(config);
    IntervalMetricReader.builder()
        .setMetricExporter(metricExporter)
        .setMetricProducers(Collections.singleton((SdkMeterProvider) GlobalMetricsProvider.get()))
        .build();
    log.info("Installed metric exporter: " + metricExporter.getClass().getName());
  }
}
