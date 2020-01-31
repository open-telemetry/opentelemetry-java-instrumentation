package io.opentelemetry.auto.tooling;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.Config;
import io.opentelemetry.auto.config.AgentConfig;
import io.opentelemetry.auto.config.ConfigProvider;
import io.opentelemetry.auto.instrumentation.api.AgentTracer;
import io.opentelemetry.auto.tooling.exporter.ExporterConfigException;
import io.opentelemetry.auto.tooling.exporter.ExporterRegistry;
import io.opentelemetry.auto.tooling.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  private static final String EXPORTER = "ota.exporter";

  /** Register agent tracer if no agent tracer is already registered. */
  public static synchronized void installAgentTracer() {
    if (Config.get().isTraceEnabled()) {
      // Try to create an exporter
      final ConfigProvider config = AgentConfig.getDefault();
      final String exporter = config.get(EXPORTER);
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
      final Tracer tracer = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");
      try {
        AgentTracer.registerIfAbsent(new AgentTracerImpl(tracer));
      } catch (final RuntimeException re) {
        log.warn("Failed to register tracer '" + tracer + "'", re);
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
