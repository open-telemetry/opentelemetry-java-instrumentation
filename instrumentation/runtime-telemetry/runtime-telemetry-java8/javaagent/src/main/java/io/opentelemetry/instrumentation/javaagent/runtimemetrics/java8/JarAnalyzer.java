/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzerUtil.addPackageChecksum;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzerUtil.addPackageDescription;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzerUtil.addPackageNameAndVersion;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzerUtil.addPackagePath;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzerUtil.addPackageType;

import com.google.common.util.concurrent.RateLimiter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import io.opentelemetry.sdk.internal.DaemonThreadFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link JarAnalyzer} processes the {@link ProtectionDomain} of each class loaded and emits an
 * event with metadata about each distinct JAR location identified.
 */
final class JarAnalyzer {

  private static final Logger LOGGER = Logger.getLogger(JarAnalyzer.class.getName());
  private static final JarAnalyzer INSTANCE = new JarAnalyzer();

  private final Set<URI> seenUris = new HashSet<>();
  private final BlockingQueue<URL> toProcess = new LinkedBlockingDeque<>();

  /** {@link #handle(ProtectionDomain)} does nothing until {@link #configure(int)} is called. */
  private Consumer<ProtectionDomain> handler = unused -> {};

  @GuardedBy("this")
  private Integer jarsPerSecond;

  @GuardedBy("this")
  private Worker worker;

  private JarAnalyzer() {}

  /** Get the {@link JarAnalyzer} singleton. */
  public static JarAnalyzer getInstance() {
    return INSTANCE;
  }

  /**
   * Configure the {@link JarAnalyzer}. If not called, {@link #handle(ProtectionDomain)} is a noop.
   *
   * @throws IllegalStateException if called multiple times
   */
  public synchronized void configure(int jarsPerSecond) {
    if (this.jarsPerSecond != null) {
      throw new IllegalStateException("JarAnalyzer has already been configured");
    }
    this.jarsPerSecond = jarsPerSecond;
    this.handler = this::handleInternal;
  }

  /**
   * Install {@link OpenTelemetry} and start processing jars if {@link #configure(int)} was called.
   *
   * @throws IllegalStateException if called multiple times
   */
  public synchronized void maybeInstall(OpenTelemetry unused) {
    if (worker != null) {
      throw new IllegalStateException("JarAnalyzer has already been installed");
    }
    if (this.jarsPerSecond == null) {
      return;
    }
    // TODO(jack-berg): Use OpenTelemetry to obtain EventEmitter when event API is stable
    EventEmitter eventEmitter =
        GlobalEventEmitterProvider.get()
            .eventEmitterBuilder(JmxRuntimeMetricsUtil.getInstrumentationName())
            .setInstrumentationVersion(JmxRuntimeMetricsUtil.getInstrumentationVersion())
            .setEventDomain("jvm")
            .build();
    this.worker = new Worker(eventEmitter, toProcess, jarsPerSecond);
    Thread workerThread =
        new DaemonThreadFactory(JarAnalyzer.class.getSimpleName() + "_WorkerThread")
            .newThread(worker);
    workerThread.start();
  }

  /**
   * Identify the JAR associated with the {@code protectionDomain} and queue it to be processed if
   * its the first time we've seen it.
   *
   * <p>NOTE: does nothing if {@link #configure(int)} has not been called.
   */
  void handle(ProtectionDomain protectionDomain) {
    this.handler.accept(protectionDomain);
  }

  private void handleInternal(ProtectionDomain protectionDomain) {
    if (protectionDomain == null) {
      return;
    }
    CodeSource codeSource = protectionDomain.getCodeSource();
    if (codeSource == null) {
      return;
    }
    URL jarUrl = codeSource.getLocation();
    if (jarUrl == null) {
      return;
    }
    URI locationUri;
    try {
      locationUri = jarUrl.toURI();
    } catch (URISyntaxException e) {
      LOGGER.log(Level.WARNING, "Unable to get URI for jar URL: " + jarUrl, e);
      return;
    }

    if (!seenUris.add(locationUri)) {
      return;
    }
    if ("jrt".equals(jarUrl.getProtocol())) {
      LOGGER.log(Level.FINEST, "Skipping processing jar for java runtime module: " + jarUrl);
      return;
    }
    if (!jarUrl.getFile().endsWith(JarAnalyzerUtil.JAR_EXTENSION)) {
      LOGGER.log(Level.INFO, "Skipping processing jar with unrecognized code location: " + jarUrl);
      return;
    }

    toProcess.add(jarUrl);
  }

  private static final class Worker implements Runnable {

    private final EventEmitter eventEmitter;
    private final BlockingQueue<URL> toProcess;
    private final RateLimiter rateLimiter;

    private Worker(EventEmitter eventEmitter, BlockingQueue<URL> toProcess, int jarsPerSecond) {
      this.eventEmitter = eventEmitter;
      this.toProcess = toProcess;
      this.rateLimiter = RateLimiter.create(jarsPerSecond);
    }

    /**
     * Continuously poll the {@link #toProcess} for JAR {@link URL}s, and process each wit {@link
     * #processUrl(URL)}.
     */
    @Override
    public void run() {
      while (!Thread.currentThread().isInterrupted()) {
        URL jarUrl = null;
        try {
          jarUrl = toProcess.poll(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        if (jarUrl == null) {
          continue;
        }
        rateLimiter.acquire();
        // TODO(jack-berg): add ability to optionally re-process urls periodically to re-emit events
        processUrl(jarUrl);
      }
      LOGGER.warning("JarAnalyzer stopped");
    }

    /**
     * Process the {@code jarUrl}, extracting metadata from it and emitting an event with the
     * content.
     */
    private void processUrl(URL jarUrl) {
      AttributesBuilder builder = Attributes.builder();

      addPackageType(builder);

      try {
        addPackageChecksum(builder, jarUrl);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error adding package checksum for jar URL: " + jarUrl, e);
      }

      try {
        addPackagePath(builder, jarUrl);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error adding package path jar URL: " + jarUrl, e);
      }

      try {
        addPackageDescription(builder, jarUrl);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error adding package description for jar URL: " + jarUrl, e);
      }

      try {
        addPackageNameAndVersion(builder, jarUrl);
      } catch (Exception e) {
        LOGGER.log(
            Level.WARNING, "Error adding package name and version for jar URL: " + jarUrl, e);
      }

      eventEmitter.emit("info", builder.build());
    }
  }
}
