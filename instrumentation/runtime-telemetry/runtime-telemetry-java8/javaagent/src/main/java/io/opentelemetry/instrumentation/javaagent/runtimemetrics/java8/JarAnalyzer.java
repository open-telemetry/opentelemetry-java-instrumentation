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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import io.opentelemetry.sdk.common.Clock;
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
 * event with metadata about each distinct archive location identified.
 */
final class JarAnalyzer {

  private static final Logger logger = Logger.getLogger(JarAnalyzer.class.getName());

  private static final JarAnalyzer INSTANCE = new JarAnalyzer();
  private static final String JAR_EXTENSION = ".jar";
  private static final String WAR_EXTENSION = ".war";
  private static final String EVENT_DOMAIN_PACKAGE = "package";
  private static final String EVENT_NAME_INFO = "info";

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
   * Install {@link OpenTelemetry} and start processing archives if {@link #configure(int)} was
   * called.
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
            .setEventDomain(EVENT_DOMAIN_PACKAGE)
            .build();
    this.worker = new Worker(eventEmitter, toProcess, jarsPerSecond);
    Thread workerThread =
        new DaemonThreadFactory(JarAnalyzer.class.getSimpleName() + "_WorkerThread")
            .newThread(worker);
    workerThread.start();
  }

  /**
   * Identify the archive (JAR or WAR) associated with the {@code protectionDomain} and queue it to
   * be processed if its the first time we've seen it.
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
    URL archiveUrl = codeSource.getLocation();
    if (archiveUrl == null) {
      return;
    }
    URI locationUri;
    try {
      locationUri = archiveUrl.toURI();
    } catch (URISyntaxException e) {
      logger.log(Level.WARNING, "Unable to get URI for code location URL: " + archiveUrl, e);
      return;
    }

    if (!seenUris.add(locationUri)) {
      return;
    }
    if ("jrt".equals(archiveUrl.getProtocol())) {
      logger.log(Level.FINEST, "Skipping processing for java runtime module: {0}", archiveUrl);
      return;
    }
    String file = archiveUrl.getFile();
    if (file.endsWith("/")) {
      logger.log(Level.FINEST, "Skipping processing non-archive code location: {0}", archiveUrl);
      return;
    }
    if (!file.endsWith(JAR_EXTENSION) && !file.endsWith(WAR_EXTENSION)) {
      logger.log(Level.INFO, "Skipping processing unrecognized code location: {0}", archiveUrl);
      return;
    }

    // Only code locations with .jar and .war extension should make it here
    toProcess.add(archiveUrl);
  }

  private static final class Worker implements Runnable {

    private final EventEmitter eventEmitter;
    private final BlockingQueue<URL> toProcess;
    private final io.opentelemetry.sdk.internal.RateLimiter rateLimiter;

    private Worker(EventEmitter eventEmitter, BlockingQueue<URL> toProcess, int jarsPerSecond) {
      this.eventEmitter = eventEmitter;
      this.toProcess = toProcess;
      this.rateLimiter =
          new io.opentelemetry.sdk.internal.RateLimiter(
              jarsPerSecond, jarsPerSecond, Clock.getDefault());
    }

    /**
     * Continuously poll the {@link #toProcess} for archive {@link URL}s, and process each wit
     * {@link #processUrl(EventEmitter, URL)}.
     */
    @Override
    public void run() {
      while (!Thread.currentThread().isInterrupted()) {
        URL archiveUrl = null;
        try {
          if (!rateLimiter.trySpend(1.0)) {
            Thread.sleep(100);
            continue;
          }
          archiveUrl = toProcess.poll(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        if (archiveUrl == null) {
          continue;
        }
        // TODO(jack-berg): add ability to optionally re-process urls periodically to re-emit events
        processUrl(eventEmitter, archiveUrl);
      }
      logger.warning("JarAnalyzer stopped");
    }
  }

  /**
   * Process the {@code archiveUrl}, extracting metadata from it and emitting an event with the
   * content.
   */
  static void processUrl(EventEmitter eventEmitter, URL archiveUrl) {
    AttributesBuilder builder = Attributes.builder();

    try {
      addPackageType(builder, archiveUrl);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error adding package type for archive URL: {0}" + archiveUrl, e);
    }

    try {
      addPackageChecksum(builder, archiveUrl);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error adding package checksum for archive URL: " + archiveUrl, e);
    }

    try {
      addPackagePath(builder, archiveUrl);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error adding package path archive URL: " + archiveUrl, e);
    }

    try {
      addPackageDescription(builder, archiveUrl);
    } catch (Exception e) {
      logger.log(
          Level.WARNING, "Error adding package description for archive URL: " + archiveUrl, e);
    }

    try {
      addPackageNameAndVersion(builder, archiveUrl);
    } catch (Exception e) {
      logger.log(
          Level.WARNING, "Error adding package name and version for archive URL: " + archiveUrl, e);
    }

    eventEmitter.emit(EVENT_NAME_INFO, builder.build());
  }
}
