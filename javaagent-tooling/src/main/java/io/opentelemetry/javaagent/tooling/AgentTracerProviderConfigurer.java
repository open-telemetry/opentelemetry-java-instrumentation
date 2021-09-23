/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.SdkTracerProviderConfigurer;
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
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(SdkTracerProviderConfigurer.class)
public class AgentTracerProviderConfigurer implements SdkTracerProviderConfigurer {
  private static final Logger logger = LoggerFactory.getLogger(AgentTracerProviderConfigurer.class);

  static final String EXPORTER_JAR_CONFIG = "otel.javaagent.experimental.exporter.jar";

  private static final String ADD_THREAD_DETAILS = "otel.javaagent.add-thread-details";

  @Override
  public void configure(
      SdkTracerProviderBuilder sdkTracerProviderBuilder, ConfigProperties config) {
    if (!Config.get().getBoolean(OpenTelemetryInstaller.JAVAAGENT_ENABLED_CONFIG, true)) {
      return;
    }

    // Register additional thread details logging span processor
    if (Config.get().getBoolean(ADD_THREAD_DETAILS, true)) {
      sdkTracerProviderBuilder.addSpanProcessor(new AddThreadDetailsSpanProcessor());
    }

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
    return !Config.get().getString("otel.traces.exporter", "").equalsIgnoreCase("logging");
  }

  private static void maybeConfigureExporterJar(SdkTracerProviderBuilder sdkTracerProviderBuilder) {
    Config config = Config.get();
    String exporterJar = config.getString(EXPORTER_JAR_CONFIG);
    if (exporterJar == null) {
      return;
    }
    installExportersFromJar(exporterJar, config, sdkTracerProviderBuilder);
  }

  // TODO remove in 1.6
  private static synchronized void installExportersFromJar(
      String exporterJar, Config config, SdkTracerProviderBuilder builder) {
    logger.warn(
        "{} is deprecated and will be removed soon! Please use {}",
        EXPORTER_JAR_CONFIG,
        ExtensionClassLoader.EXTENSIONS_CONFIG);
    URL url;
    try {
      url = new File(exporterJar).toURI().toURL();
    } catch (MalformedURLException e) {
      logger.warn("Filename could not be parsed: {}. Exporter is not installed", exporterJar);
      logger.warn("No valid exporter found. Tracing will run but spans are dropped");
      return;
    }
    ExporterClassLoader exporterLoader =
        new ExporterClassLoader(url, OpenTelemetryInstaller.class.getClassLoader());

    SpanExporterFactory spanExporterFactory =
        getExporterFactory(SpanExporterFactory.class, exporterLoader);

    if (spanExporterFactory != null) {
      installSpanExporter(spanExporterFactory, config, builder);
    } else {
      logger.warn("No span exporter found in {}", exporterJar);
      logger.warn("No valid exporter found. Tracing will run but spans are dropped");
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
        logger.warn(
            "Exporter JAR defines more than one {}. Only the first one found will be used",
            service.getName());
      }
      return factory;
    }
    return null;
  }

  private static void installSpanExporter(
      SpanExporterFactory spanExporterFactory, Config config, SdkTracerProviderBuilder builder) {
    SpanExporter spanExporter = spanExporterFactory.fromConfig(config.asJavaProperties());
    SpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter).build();
    builder.addSpanProcessor(spanProcessor);
    logger.info("Installed span exporter: {}", spanExporter.getClass().getName());
  }

  private static void installMetricExporter(
      MetricExporterFactory metricExporterFactory, Config config) {
    MetricExporter metricExporter = metricExporterFactory.fromConfig(config.asJavaProperties());
    IntervalMetricReader.builder()
        .setMetricExporter(metricExporter)
        .setMetricProducers(Collections.singleton((SdkMeterProvider) GlobalMeterProvider.get()))
        .buildAndStart();
    logger.info("Installed metric exporter: {}", metricExporter.getClass().getName());
  }
}
