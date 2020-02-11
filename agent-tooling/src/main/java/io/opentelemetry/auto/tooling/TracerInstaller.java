package io.opentelemetry.auto.tooling;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.exportersupport.ExporterFactory;
import io.opentelemetry.auto.tooling.exporter.ExporterConfigException;
import io.opentelemetry.auto.tooling.exporter.ExporterRegistry;
import io.opentelemetry.auto.tooling.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.jar.Manifest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  private static ExporterClassLoader exporterLoader;

  /** Register agent tracer if no agent tracer is already registered. */
  public static synchronized void installAgentTracer() {
    if (Config.get().isTraceEnabled()) {

      // Try to create an exporter
      SpanExporter exporter = null;
      final String expName = Config.get().getExporter();
      if (expName != null) {
        try {
          final SpanExporterFactory f = ExporterRegistry.getInstance().getFactory(expName);
          exporter = f.newExporter();
          log.info("Loaded span exporter: " + expName);
        } catch (final ExporterConfigException e) {
          log.warn("Error loading exporter. Spans will be dropped", e);
        }
      } else {
        final String exporterJar = Config.get().getExporterJar();
        if (exporterJar != null) {
          exporter = loadFromJar(exporterJar);
        }
      }
      if (exporter != null) {
        OpenTelemetrySdk.getTracerFactory()
            .addSpanProcessor(SimpleSpansProcessor.newBuilder(exporter).build());
        log.info("Installed span exporter: " + exporter.getClass().getCanonicalName());
      } else {
        log.warn("No exporter is specified. Tracing will run but spans are dropped");
      }
    } else {
      log.info("Tracing is disabled.");
    }
  }

  @VisibleForTesting
  private static synchronized SpanExporter loadFromJar(final String exporterJar) {
    final URL url;
    try {
      url = new File(exporterJar).toURI().toURL();
    } catch (final MalformedURLException e) {
      log.warn("Filename could not be parsed: " + exporterJar + ". Exporter is not installed");
      return null;
    }

    // Locate the name of the bootstrap class and try to load it
    final Manifest mf;
    exporterLoader =
        new ExporterClassLoader(new URL[] {url}, TracerInstaller.class.getClassLoader());
    final ServiceLoader<ExporterFactory> sl =
        ServiceLoader.load(ExporterFactory.class, exporterLoader);
    final Iterator<ExporterFactory> itor = sl.iterator();
    if (itor.hasNext()) {
      final ExporterFactory f = itor.next();
      if (itor.hasNext()) {
        log.warn(
            "Exporter JAR defines more than one factory. Only the first one found will be used");
      }
      return f.fromConfig(new DefaultConfigProvider("exporter"));
    }
    log.warn("No matching providers in jar " + exporterJar);
    return null;
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
