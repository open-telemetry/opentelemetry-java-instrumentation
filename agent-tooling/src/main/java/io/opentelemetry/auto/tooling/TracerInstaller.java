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

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.exportersupport.MetricExporterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.contrib.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.BatchSpansProcessor;
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
  public static synchronized void installAgentTracer() {
    if (Config.get().isTraceEnabled()) {

      // Try to create an exporter
      final String exporterJar = Config.get().getExporterJar();
      if (exporterJar != null) {
        installExportersFromJar(exporterJar);
      } else {
        log.warn("No exporter is specified. Tracing will run but spans are dropped");
      }
    } else {
      log.info("Tracing is disabled.");
    }
  }

  @VisibleForTesting
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
        new ExporterClassLoader(new URL[] {url}, TracerInstaller.class.getClassLoader());

    final SpanExporterFactory spanExporterFactory =
        getExporterFactory(SpanExporterFactory.class, exporterLoader);
    if (spanExporterFactory != null) {
      final SpanExporter spanExporter = spanExporterFactory.fromConfig(config);
      OpenTelemetrySdk.getTracerProvider()
          .addSpanProcessor(
              BatchSpansProcessor.create(
                  spanExporter,
                  BatchSpansProcessor.Config
                      .loadFromDefaultSources())); // Loads configuration for SpansProcessor from
                                                   // env vars if exists.
      log.info("Installed span exporter: " + spanExporter.getClass().getName());
    } else {
      log.warn("No matching providers in jar " + exporterJar);
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
    }

    final MetricExporterFactory metricExporterFactory =
        getExporterFactory(MetricExporterFactory.class, exporterLoader);
    if (metricExporterFactory != null) {
      final MetricExporter metricExporter = metricExporterFactory.fromConfig(config);
      IntervalMetricReader.builder()
          .setMetricExporter(metricExporter)
          .setMetricProducers(
              Collections.singleton(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
          .build();
      log.info("Installed metric exporter: " + metricExporter.getClass().getName());
    }
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

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
