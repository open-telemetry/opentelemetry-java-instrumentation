/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static io.opentelemetry.javaagent.instrumentation.runtimetelemetry.JarDetails.EAR_EXTENSION;
import static io.opentelemetry.javaagent.instrumentation.runtimetelemetry.JarDetails.JAR_EXTENSION;
import static io.opentelemetry.javaagent.instrumentation.runtimetelemetry.JarDetails.WAR_EXTENSION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.internal.DaemonThreadFactory;
import io.opentelemetry.sdk.common.internal.RateLimiter;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * {@link JarAnalyzer} is a {@link ClassFileTransformer} which processes the {@link
 * ProtectionDomain} of each class loaded and emits an event with metadata about each distinct
 * archive location identified.
 */
final class JarAnalyzer implements ClassFileTransformer {

  private static final java.util.logging.Logger logger =
      java.util.logging.Logger.getLogger(JarAnalyzer.class.getName());

  private static final String EVENT_NAME_INFO = "package.info";
  static final AttributeKey<String> PACKAGE_NAME = AttributeKey.stringKey("package.name");
  static final AttributeKey<String> PACKAGE_VERSION = AttributeKey.stringKey("package.version");
  static final AttributeKey<String> PACKAGE_TYPE = AttributeKey.stringKey("package.type");
  static final AttributeKey<String> PACKAGE_DESCRIPTION =
      AttributeKey.stringKey("package.description");
  static final AttributeKey<String> PACKAGE_CHECKSUM = AttributeKey.stringKey("package.checksum");
  static final AttributeKey<String> PACKAGE_CHECKSUM_ALGORITHM =
      AttributeKey.stringKey("package.checksum_algorithm");
  static final AttributeKey<String> PACKAGE_PATH = AttributeKey.stringKey("package.path");

  private final Set<URI> seenUris = new HashSet<>();
  private final BlockingQueue<URL> toProcess = new LinkedBlockingDeque<>();

  private JarAnalyzer(OpenTelemetry openTelemetry, String instrumentationName, int jarsPerSecond) {
    String instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    Logger logger =
        openTelemetry
            .getLogsBridge()
            .loggerBuilder(instrumentationName)
            .setInstrumentationVersion(instrumentationVersion)
            .build();
    Worker worker = new Worker(logger, toProcess, jarsPerSecond);
    Thread workerThread =
        new DaemonThreadFactory(JarAnalyzer.class.getSimpleName() + "_WorkerThread")
            .newThread(worker);
    workerThread.start();
  }

  /** Create {@link JarAnalyzer} and start the worker thread. */
  // TODO can remove instrumentationName parameter in 3.0
  public static JarAnalyzer create(
      OpenTelemetry openTelemetry, String instrumentationName, int jarsPerSecond) {
    return new JarAnalyzer(openTelemetry, instrumentationName, jarsPerSecond);
  }

  /**
   * Identify the archive (JAR or WAR) associated with the {@code protectionDomain} and queue it to
   * be processed if its the first time we've seen it.
   */
  @Override
  public byte[] transform(
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer) {
    handle(protectionDomain);
    return null;
  }

  private void handle(ProtectionDomain protectionDomain) {
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
      logger.log(WARNING, "Unable to get URI for code location URL: " + archiveUrl, e);
      return;
    }

    if (!seenUris.add(locationUri)) {
      return;
    }
    if ("jrt".equals(archiveUrl.getProtocol())) {
      logger.log(FINEST, "Skipping processing for java runtime module: {0}", archiveUrl);
      return;
    }
    String file = archiveUrl.getFile();
    if (file.endsWith("/")) {
      logger.log(FINEST, "Skipping processing non-archive code location: {0}", archiveUrl);
      return;
    }
    if (!file.endsWith(JAR_EXTENSION)
        && !file.endsWith(WAR_EXTENSION)
        && !file.endsWith(EAR_EXTENSION)) {
      logger.log(INFO, "Skipping processing unrecognized code location: {0}", archiveUrl);
      return;
    }

    // Payara 5 and 6 have url with file protocol that fail on openStream with
    // java.io.IOException: no entry name specified
    //   at
    // java.base/sun.net.www.protocol.jar.JarURLConnection.getInputStream(JarURLConnection.java:160)
    // To avoid this here we recreate the URL when it points to a file.
    if ("file".equals(archiveUrl.getProtocol())) {
      try {
        File archiveFile = new File(archiveUrl.toURI().getSchemeSpecificPart());
        if (archiveFile.exists() && archiveFile.isFile()) {
          archiveUrl = archiveFile.toURI().toURL();
        }
      } catch (Exception e) {
        logger.log(WARNING, "Unable to normalize location URL: " + archiveUrl, e);
      }
    }

    // Only code locations with .jar and .war extension should make it here
    toProcess.add(archiveUrl);
  }

  private static final class Worker implements Runnable {

    private final Logger logger;
    private final BlockingQueue<URL> toProcess;
    private final RateLimiter rateLimiter;

    private Worker(Logger logger, BlockingQueue<URL> toProcess, int jarsPerSecond) {
      this.logger = logger;
      this.toProcess = toProcess;
      this.rateLimiter = new RateLimiter(jarsPerSecond, jarsPerSecond, Clock.getDefault());
    }

    /**
     * Continuously poll the {@link #toProcess} for archive {@link URL}s, and process each wit
     * {@link #processUrl(Logger, URL)}.
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
          archiveUrl = toProcess.poll(100, MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        if (archiveUrl == null) {
          continue;
        }
        try {
          // TODO(jack-berg): add ability to optionally re-process urls periodically to re-emit
          // events
          processUrl(logger, archiveUrl);
        } catch (Throwable e) {
          JarAnalyzer.logger.log(
              WARNING, "Unexpected error processing archive URL: " + archiveUrl, e);
        }
      }
      JarAnalyzer.logger.warning("JarAnalyzer stopped");
    }
  }

  /**
   * Process the {@code archiveUrl}, extracting metadata from it and emitting an event with the
   * content.
   */
  static void processUrl(Logger logger, URL archiveUrl) {
    JarDetails jarDetails;
    try {
      jarDetails = JarDetails.forUrl(archiveUrl);
    } catch (IOException e) {
      JarAnalyzer.logger.log(WARNING, "Error reading package for archive URL: " + archiveUrl, e);
      return;
    }
    AttributesBuilder builder = Attributes.builder();

    String packagePath = jarDetails.packagePath();
    if (packagePath != null) {
      builder.put(PACKAGE_PATH, packagePath);
    }

    String packageType = jarDetails.packageType();
    if (packageType != null) {
      builder.put(PACKAGE_TYPE, packageType);
    }

    String packageName = jarDetails.packageName();
    if (packageName != null) {
      builder.put(PACKAGE_NAME, packageName);
    }

    String packageVersion = jarDetails.version();
    if (packageVersion != null) {
      builder.put(PACKAGE_VERSION, packageVersion);
    }

    String packageDescription = jarDetails.packageDescription();
    if (packageDescription != null) {
      builder.put(PACKAGE_DESCRIPTION, packageDescription);
    }

    String packageChecksum = jarDetails.computeSha1();
    builder.put(PACKAGE_CHECKSUM, packageChecksum);
    builder.put(PACKAGE_CHECKSUM_ALGORITHM, "SHA1");

    logger
        .logRecordBuilder()
        .setEventName(EVENT_NAME_INFO)
        .setAllAttributes(builder.build())
        .emit();
  }
}
