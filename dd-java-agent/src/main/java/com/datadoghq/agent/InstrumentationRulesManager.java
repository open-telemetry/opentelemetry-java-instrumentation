package com.datadoghq.agent;

import com.google.common.collect.Maps;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * This manager is loaded at pre-main. It loads all the scripts contained in all the 'oatrules.btm'
 * resource files.
 */
@Slf4j
public class InstrumentationRulesManager {

  private static final String INTEGRATION_RULES = "integration-rules.btm";
  private static final String HELPERS_NAME = "/helpers.jar.zip";

  private static final Object SYNC = new Object();

  private final TracingAgentConfig config;
  private final AgentRulesManager agentRulesManager;
  private final ClassLoaderIntegrationInjector injector;
  private final InstrumentationChecker checker = new InstrumentationChecker();

  private final Set<ClassLoader> initializedClassloaders =
      Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());

  public InstrumentationRulesManager(
      final TracingAgentConfig config, final AgentRulesManager agentRulesManager) {
    this.config = config;
    this.agentRulesManager = agentRulesManager;
    final InputStream helpersStream = this.getClass().getResourceAsStream(HELPERS_NAME);
    final ZipInputStream stream = new ZipInputStream(helpersStream);
    final Map<ZipEntry, byte[]> helperEntries = Maps.newHashMap();
    try {
      ZipEntry entry = stream.getNextEntry();
      while (entry != null) {
        if (entry.isDirectory()) {
          entry = stream.getNextEntry();
          continue;
        }
        // this is a buffer, so the long->int truncation is not an issue.
        final ByteArrayOutputStream os = new ByteArrayOutputStream((int) entry.getSize());

        int n;
        final byte[] buf = new byte[1024];
        while ((n = stream.read(buf, 0, 1024)) > -1) {
          os.write(buf, 0, n);
        }
        helperEntries.put(entry, os.toByteArray());
        entry = stream.getNextEntry();
      }
    } catch (final IOException e) {
      log.error("Error extracting helpers", e);
    }
    injector = new ClassLoaderIntegrationInjector(helperEntries);
  }

  public static void registerClassLoad() {
    log.debug("Register called by class initializer.");
    registerClassLoad(Thread.currentThread().getContextClassLoader());
  }

  public static void registerClassLoad(final Object obj) {
    if (AgentRulesManager.INSTANCE == null) {
      return;
    }
    final ClassLoader cl;
    if (obj instanceof ClassLoader) {
      cl = (ClassLoader) obj;
      log.debug("Calling initialize with {}", cl);
    } else {
      cl = obj.getClass().getClassLoader();
      log.debug("Calling initialize with {} and classloader {}", obj, cl);
    }

    AgentRulesManager.INSTANCE.instrumentationRulesManager.initialize(cl);
  }

  /**
   * This method is separated out from initialize to allow Spring Boot's LaunchedURLClassLoader to
   * call it once it is loaded.
   *
   * @param classLoader
   */
  public void initialize(final ClassLoader classLoader) {
    synchronized (classLoader) {
      if (initializedClassloaders.contains(classLoader)) {
        return;
      }
      initializedClassloaders.add(classLoader);
    }
    log.info("Initializing on classloader {}", classLoader);

    injector.inject(classLoader);

    initTracer();
  }

  private void initTracer() {
    synchronized (SYNC) {
      if (!GlobalTracer.isRegistered()) {
        // Try to obtain a tracer using the TracerResolver
        final Tracer resolved = TracerResolver.resolveTracer();
        if (resolved != null) {
          try {
            GlobalTracer.register(resolved);
          } catch (final RuntimeException re) {
            log.warn("Failed to register tracer '" + resolved + "'", re);
          }
        }
      }
    }
  }
}
