package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.tooling.exporter.ExporterConfigException;
import io.opentelemetry.auto.tooling.exporter.ExporterRegistry;
import io.opentelemetry.auto.tooling.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.Manifest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  private static ExporterClassLoader exporterLoader;

  /** Register agent tracer if no agent tracer is already registered. */
  public static synchronized void installAgentTracer() {
    System.out.println("Entering installAgentTracer");
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
        System.out.println("Exporter jar is: " + exporterJar);
        if (exporterJar != null) {
          exporter = loadFromJar(exporterJar);
        }
      }
      System.out.println("Exporter is: " + exporter);
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

  private static SpanExporter loadFromJar(final String exporterJar) {
    try {
      final URL url = new File(exporterJar).toURI().toURL();

      System.out.println("Trying to load jar: " + url);

      // Locate the name of the bootstrap class and try to load it
      final Manifest mf;
      exporterLoader = new ExporterClassLoader(new URL[] {url}, null);
      try (final InputStream in = exporterLoader.getResourceAsStream("META-INF/MANIFEST.MF")) {
        if (in == null) {
          log.warn("Could not load MANIFEST.MF from exporter jar");
          return null;
        }
        mf = new Manifest(in);
        System.out.println("Manifest:" + mf.getMainAttributes());
      }

      final String bootstrap = mf.getMainAttributes().getValue("Ota-Bootstrap-Class");
      for (final Object key : mf.getMainAttributes().keySet()) {
        System.out.println(key + "->" + mf.getMainAttributes().get(key));
      }
      if (bootstrap == null) {
        log.warn("Could not find name of bootstrap class in MANIFEST.MF");
        return null;
      }
      System.out.println("Bootstrap: " + bootstrap);
      final Class<?> bootstrapClass = exporterLoader.loadClass(bootstrap);

      // Use reflection to call the bootstrap method. It should return a SpanRepoter.
      final Method m = bootstrapClass.getMethod("createSpanExporter");
      return (SpanExporter) m.invoke(null);

    } catch (final MalformedURLException e) {
      log.warn("Could not locate the exporter jar: " + exporterJar, e);
    } catch (final IOException e) {
      log.warn("Could not load the exporter jar: " + exporterJar, e);
    } catch (final ClassNotFoundException e) {
      log.warn("Could not load the bootstrap class for : " + exporterJar, e);
    } catch (final NoSuchMethodException e) {
      log.warn("Could locate the boostrap method for : " + exporterJar, e);
    } catch (final InvocationTargetException e) {
      log.warn("Could execute the boostrap method for : " + exporterJar, e);
    } catch (final IllegalAccessException e) {
      log.warn("Bootstrap method not public for : " + exporterJar, e);
    }
    return null;
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
