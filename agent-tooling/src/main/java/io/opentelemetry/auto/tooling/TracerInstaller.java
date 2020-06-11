/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.contrib.auto.config.MetricExporterFactory;
import io.opentelemetry.sdk.contrib.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register agent tracer if no agent tracer is already registered. */
  @SuppressWarnings("unused")
  public static synchronized void installAgentTracer() {
    if (Config.get().isTraceEnabled()) {

      configure();
      // Try to create an exporter from external jar file
      final String exporterJar = Config.get().getExporterJar();
      if (exporterJar != null) {
        installExportersFromJar(exporterJar);
      } else {
        // Try to create embedded exporter
        installExporters(Config.get().getExporter());
      }
    } else {
      log.info("Tracing is disabled.");
    }

    PropagatorsInitializer.initializePropagators(Config.get().getPropagators());
  }

  private static synchronized void installExporters(final String exporterName) {
    final SpanExporterFactory spanExporterFactory = findSpanExporterFactory(exporterName);
    if (spanExporterFactory != null) {
      final DefaultExporterConfig config = new DefaultExporterConfig("exporter");
      installExporter(spanExporterFactory, config);
    } else {
      log.warn("No {} span exporter found", exporterName);
      log.warn("No valid span exporter found. Tracing will run but spans are dropped");
    }
  }

  private static SpanExporterFactory findSpanExporterFactory(String exporterName) {
    final ServiceLoader<SpanExporterFactory> serviceLoader =
        ServiceLoader.load(SpanExporterFactory.class, TracerInstaller.class.getClassLoader());

    for (SpanExporterFactory spanExporterFactory : serviceLoader) {
      if (spanExporterFactory
          .getClass()
          .getSimpleName()
          .toLowerCase()
          .startsWith(exporterName.toLowerCase())) {
        return spanExporterFactory;
      }
    }
    return null;
  }

  private static synchronized void installExportersFromJar(final String exporterJar) {
    final URL url;
    try {
      url = new File(exporterJar).toURI().toURL();
    } catch (final MalformedURLException e) {
      log.warn("Filename could not be parsed: " + exporterJar + ". Exporter is not installed");
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
      return;
    }
    final DefaultExporterConfig config = new DefaultExporterConfig("exporter");
    final ExporterClassLoader exporterLoader =
        new ExporterClassLoader(url, TracerInstaller.class.getClassLoader());

    final SpanExporterFactory spanExporterFactory =
        getExporterFactory(SpanExporterFactory.class, exporterLoader);

    if (spanExporterFactory != null) {
      installExporter(spanExporterFactory, config);
    } else {
      log.warn("No span exporter found in {}", exporterJar);
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
    }

    final MetricExporterFactory metricExporterFactory =
        getExporterFactory(MetricExporterFactory.class, exporterLoader);
    if (metricExporterFactory != null) {
      installExporter(metricExporterFactory, config);
    }
  }

  private static void installExporter(
      MetricExporterFactory metricExporterFactory, DefaultExporterConfig config) {
    final MetricExporter metricExporter = metricExporterFactory.fromConfig(config);
    IntervalMetricReader.builder()
        .setMetricExporter(metricExporter)
        .setMetricProducers(
            Collections.singleton(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
        .build();
    log.info("Installed metric exporter: " + metricExporter.getClass().getName());
  }

  private static void installExporter(
      SpanExporterFactory spanExporterFactory, DefaultExporterConfig config) {
    final SpanExporter spanExporter = spanExporterFactory.fromConfig(config);
    final BatchSpanProcessor spanProcessor =
        BatchSpanProcessor.newBuilder(spanExporter)
            .readEnvironmentVariables()
            .readSystemProperties()
            .build();
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(spanProcessor);
    log.info("Installed span exporter: " + spanExporter.getClass().getName());
  }

  private static <F> F getExporterFactory(
      final Class<F> service, final ExporterClassLoader exporterLoader) {
    final ServiceLoader<F> serviceLoader = ServiceLoader.load(service, exporterLoader);
    final Iterator<F> i = serviceLoader.iterator();
    if (i.hasNext()) {
      final F factory = i.next();
      if (i.hasNext()) {
        log.warn(
            "Exporter JAR defines more than one {}. Only the first one found will be used",
            service.getName());
      }
      return factory;
    }
    return null;
  }

  private static void configure() {
    /* Update trace config from env vars or sys props */
    final TraceConfig activeTraceConfig =
        OpenTelemetrySdk.getTracerProvider().getActiveTraceConfig();
    OpenTelemetrySdk.getTracerProvider()
        .updateActiveTraceConfig(
            activeTraceConfig
                .toBuilder()
                .readEnvironmentVariables()
                .readSystemProperties()
                .build());
  }

  @SuppressWarnings("unused")
  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
