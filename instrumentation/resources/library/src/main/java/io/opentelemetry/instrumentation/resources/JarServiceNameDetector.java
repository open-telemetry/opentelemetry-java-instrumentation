/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.FINE;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A {@link ResourceProvider} that will attempt to detect the application name from the jar name.
 */
@AutoService(ResourceProvider.class)
public final class JarServiceNameDetector implements ConditionalResourceProvider {

  private static final Logger logger = Logger.getLogger(JarServiceNameDetector.class.getName());

  private final Supplier<String[]> getProcessHandleArguments;
  private final Function<String, String> getSystemProperty;
  private final Predicate<Path> fileExists;

  @SuppressWarnings("unused") // SPI
  public JarServiceNameDetector() {
    this(ProcessArguments::getProcessArguments, System::getProperty, Files::isRegularFile);
  }

  // visible for tests
  JarServiceNameDetector(
      Supplier<String[]> getProcessHandleArguments,
      Function<String, String> getSystemProperty,
      Predicate<Path> fileExists) {
    this.getProcessHandleArguments = getProcessHandleArguments;
    this.getSystemProperty = getSystemProperty;
    this.fileExists = fileExists;
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    Path jarPath = getJarPathFromProcessHandle();
    if (jarPath == null) {
      jarPath = getJarPathFromSunCommandLine();
    }
    if (jarPath == null) {
      return Resource.empty();
    }
    String serviceName = getServiceName(jarPath);
    logger.log(FINE, "Auto-detected service name from the jar file name: {0}", serviceName);
    return Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName));
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    String serviceName = config.getString("otel.service.name");
    Map<String, String> resourceAttributes = config.getMap("otel.resource.attributes");
    return serviceName == null
        && !resourceAttributes.containsKey(ResourceAttributes.SERVICE_NAME.getKey())
        && "unknown_service:java".equals(existing.getAttribute(ResourceAttributes.SERVICE_NAME));
  }

  @Nullable
  private Path getJarPathFromProcessHandle() {
    String[] javaArgs = getProcessHandleArguments.get();
    for (int i = 0; i < javaArgs.length; ++i) {
      if ("-jar".equals(javaArgs[i]) && (i < javaArgs.length - 1)) {
        return Paths.get(javaArgs[i + 1]);
      }
    }
    return null;
  }

  @Nullable
  private Path getJarPathFromSunCommandLine() {
    // the jar file is the first argument in the command line string
    String programArguments = getSystemProperty.apply("sun.java.command");
    if (programArguments == null) {
      return null;
    }

    // Take the path until the first space. If the path doesn't exist extend it up to the next
    // space. Repeat until a path that exists is found or input runs out.
    int next = 0;
    while (true) {
      int nextSpace = programArguments.indexOf(' ', next);
      if (nextSpace == -1) {
        return pathIfExists(programArguments);
      }
      Path path = pathIfExists(programArguments.substring(0, nextSpace));
      next = nextSpace + 1;
      if (path != null) {
        return path;
      }
    }
  }

  @Nullable
  private Path pathIfExists(String programArguments) {
    Path candidate;
    try {
      candidate = Paths.get(programArguments);
    } catch (InvalidPathException e) {
      return null;
    }
    return fileExists.test(candidate) ? candidate : null;
  }

  private static String getServiceName(Path jarPath) {
    String jarName = jarPath.getFileName().toString();
    int dotIndex = jarName.lastIndexOf(".");
    return dotIndex == -1 ? jarName : jarName.substring(0, dotIndex);
  }

  @Override
  public int order() {
    // make it run later than the SpringBootServiceNameDetector
    return 1000;
  }
}
