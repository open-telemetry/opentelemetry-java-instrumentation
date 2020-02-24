package io.opentelemetry.auto.tooling;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.exportersupport.SpanExporterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
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
        final SpanExporter exporter = loadFromJar(exporterJar);
        if (exporter != null) {
          OpenTelemetrySdk.getTracerFactory()
              .addSpanProcessor(SimpleSpansProcessor.newBuilder(exporter).build());
          log.info("Installed span exporter: " + exporter.getClass().getCanonicalName());
        } else {
          log.warn("No valid exporter found. Tracing will run but spans are dropped");
        }
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

    final ExporterClassLoader exporterLoader =
        new ExporterClassLoader(new URL[] {url}, TracerInstaller.class.getClassLoader());
    final ServiceLoader<SpanExporterFactory> sl =
        ServiceLoader.load(SpanExporterFactory.class, exporterLoader);
    final Iterator<SpanExporterFactory> itor = sl.iterator();
    if (itor.hasNext()) {
      final SpanExporterFactory f = itor.next();
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
