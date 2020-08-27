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

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.spi.TracerCustomizer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.extensions.auto.config.MetricExporterFactory;
import io.opentelemetry.sdk.extensions.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracerInstaller {

  private static final Logger log = LoggerFactory.getLogger(TracerInstaller.class);

  /** Register agent tracer if no agent tracer is already registered. */
  @SuppressWarnings("unused")
  public static synchronized void installAgentTracer() {
    if (Config.get().isTraceEnabled()) {

      configure();
      // Try to create an exporter from external jar file
      String exporterJar = Config.get().getExporterJar();
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

  private static synchronized void installExporters(String exporterName) {
    SpanExporterFactory spanExporterFactory = findSpanExporterFactory(exporterName);
    if (spanExporterFactory != null) {
      DefaultExporterConfig config = new DefaultExporterConfig("exporter");
      installExporter(spanExporterFactory, config);
    } else {
      log.warn("No {} span exporter found", exporterName);
      log.warn("No valid span exporter found. Tracing will run but spans are dropped");
    }

    MetricExporterFactory metricExporterFactory = findMetricExporterFactory(exporterName);
    if (metricExporterFactory != null) {
      DefaultExporterConfig config = new DefaultExporterConfig("exporter");
      installExporter(metricExporterFactory, config);
    } else {
      log.debug("No {} metric exporter found", exporterName);
    }
  }

  private static MetricExporterFactory findMetricExporterFactory(String exporterName) {
    ServiceLoader<MetricExporterFactory> serviceLoader =
        ServiceLoader.load(MetricExporterFactory.class, TracerInstaller.class.getClassLoader());

    for (MetricExporterFactory metricExporterFactory : serviceLoader) {
      if (metricExporterFactory
          .getClass()
          .getSimpleName()
          .toLowerCase()
          .startsWith(exporterName.toLowerCase())) {
        return metricExporterFactory;
      }
    }
    return null;
  }

  private static SpanExporterFactory findSpanExporterFactory(String exporterName) {
    ServiceLoader<SpanExporterFactory> serviceLoader =
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

  private static synchronized void installExportersFromJar(String exporterJar) {
    URL url;
    try {
      url = new File(exporterJar).toURI().toURL();
    } catch (MalformedURLException e) {
      log.warn("Filename could not be parsed: " + exporterJar + ". Exporter is not installed");
      log.warn("No valid exporter found. Tracing will run but spans are dropped");
      return;
    }
    DefaultExporterConfig config = new DefaultExporterConfig("exporter");
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
      MetricExporterFactory metricExporterFactory, DefaultExporterConfig config) {
    MetricExporter metricExporter = metricExporterFactory.fromConfig(config);
    IntervalMetricReader.builder()
        .readEnvironmentVariables()
        .readSystemProperties()
        .setMetricExporter(metricExporter)
        .setMetricProducers(
            Collections.singleton(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
        .build();
    log.info("Installed metric exporter: " + metricExporter.getClass().getName());
  }

  private static void installExporter(
      SpanExporterFactory spanExporterFactory, DefaultExporterConfig config) {
    SpanExporter spanExporter = spanExporterFactory.fromConfig(config);
    BatchSpanProcessor spanProcessor =
        BatchSpanProcessor.newBuilder(spanExporter)
            .readEnvironmentVariables()
            .readSystemProperties()
            .build();
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(spanProcessor);
    log.info("Installed span exporter: " + spanExporter.getClass().getName());
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

  private static void configure() {
    TracerSdkProvider tracerSdkProvider = OpenTelemetrySdk.getTracerProvider();

    // Register additional thread details logging span processor
    tracerSdkProvider.addSpanProcessor(new AddThreadDetailsSpanProcessor());

    // Execute any user-provided (usually vendor-provided) configuration logic.
    ServiceLoader<TracerCustomizer> serviceLoader =
        ServiceLoader.load(TracerCustomizer.class, TracerInstaller.class.getClassLoader());
    for (TracerCustomizer customizer : serviceLoader) {
      customizer.configure(tracerSdkProvider);
    }

    /* Update trace config from env vars or sys props */
    TraceConfig activeTraceConfig = tracerSdkProvider.getActiveTraceConfig();
    tracerSdkProvider.updateActiveTraceConfig(
        activeTraceConfig.toBuilder().readEnvironmentVariables().readSystemProperties().build());
  }

  @SuppressWarnings("unused")
  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
