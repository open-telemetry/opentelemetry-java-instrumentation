package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.tooling.exporter.ExporterConfigException;
import io.opentelemetry.auto.tooling.exporter.ExporterRegistry;
import io.opentelemetry.auto.tooling.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {

  /** Register agent tracer if no agent tracer is already registered. */
  public static synchronized void installAgentTracer() {
    if (Config.get().isTraceEnabled()) {

      // Try to create an exporter
      final String exporter = Config.get().getExporter();
      if (exporter != null) {
        try {
          final SpanExporterFactory f = ExporterRegistry.getInstance().getFactory(exporter);
          OpenTelemetrySdk.getTracerFactory()
              .addSpanProcessor(SimpleSpansProcessor.newBuilder(f.newExporter()).build());
          log.info("Loaded span exporter: " + exporter);
        } catch (final ExporterConfigException e) {
          log.warn("Error loading exporter. Spans will be dropped", e);
        }
      } else {
        log.warn("No exporter is specified. Tracing will run but spans are dropped");
      }
    } else {
      log.debug("Tracing is disabled.");
    }
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
