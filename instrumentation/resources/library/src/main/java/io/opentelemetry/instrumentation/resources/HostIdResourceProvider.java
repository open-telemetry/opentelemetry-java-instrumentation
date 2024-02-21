/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** {@link ResourceProvider} for automatically configuring <code>host.id</code>. */
public final class HostIdResourceProvider implements ConditionalResourceProvider {

  private static final Logger logger = Logger.getLogger(HostIdResourceProvider.class.getName());

  public static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");
  public static final String REGISTRY_QUERY =
      "reg query HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography /v MachineGuid";

  private final Supplier<OsType> getOsType;

  private final Function<Path, List<String>> pathReader;

  private final Supplier<ExecResult> queryWindowsRegistry;

  enum OsType {
    WINDOWS,
    LINUX
  }

  static class ExecResult {
    int exitCode;
    List<String> lines;

    public ExecResult(int exitCode, List<String> lines) {
      this.exitCode = exitCode;
      this.lines = lines;
    }
  }

  public HostIdResourceProvider() {
    this(
        HostIdResourceProvider::getOsType,
        path -> {
          try {
            return Files.readAllLines(path);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        },
        HostIdResourceProvider::queryWindowsRegistry);
  }

  // Visible for testing
  HostIdResourceProvider(
      Supplier<OsType> getOsType,
      Function<Path, List<String>> pathReader,
      Supplier<ExecResult> queryWindowsRegistry) {
    this.getOsType = getOsType;
    this.pathReader = pathReader;
    this.queryWindowsRegistry = queryWindowsRegistry;
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    OsType osType = getOsType.get();
    switch (osType) {
      case WINDOWS:
        return readWindowsGuid();
      case LINUX:
        return readLinuxMachineId();
    }
    throw new IllegalStateException("Unsupported OS type: " + osType);
  }

  private Resource readLinuxMachineId() {
    Path path = FileSystems.getDefault().getPath("/etc/machine-id");
    try {
      List<String> lines = pathReader.apply(path);
      if (lines.isEmpty()) {
        logger.warning("Failed to read /etc/machine-id: empty file");
        return Resource.empty();
      }
      return Resource.create(Attributes.of(HOST_ID, lines.get(0)));
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Failed to read /etc/machine-id", e);
      return Resource.empty();
    }
  }

  private static OsType getOsType() {
    String osName = System.getProperty("os.name");
    return osName != null && osName.startsWith("Windows") ? OsType.WINDOWS : OsType.LINUX;
  }

  private Resource readWindowsGuid() {

    try {
      ExecResult execResult = queryWindowsRegistry.get();

      if (execResult.exitCode != 0) {
        logger.warning(
            "Failed to read Windows registry. Exit code: "
                + execResult.exitCode
                + " Output: "
                + String.join("\n", execResult.lines));
        return Resource.empty();
      }

      for (String line : execResult.lines) {
        if (line.contains("MachineGuid")) {
          String[] parts = line.trim().split("\\s+");
          if (parts.length == 3) {
            return Resource.create(Attributes.of(HOST_ID, parts[2]));
          }
        }
      }
      logger.warning(
          "Failed to read Windows registry: No MachineGuid found in output: " + execResult.lines);
      return Resource.empty();
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Failed to read Windows registry", e);
      return Resource.empty();
    }
  }

  private static ExecResult queryWindowsRegistry() {
    try {
      Process process = Runtime.getRuntime().exec(REGISTRY_QUERY);

      if (process.waitFor() != 0) {
        return new ExecResult(process.exitValue(), getLines(process.getErrorStream()));
      }

      return new ExecResult(0, getLines(process.getInputStream()));
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> getLines(InputStream inputStream) {
    return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.toList());
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    return !config.getMap("otel.resource.attributes").containsKey(HOST_ID.getKey())
        && existing.getAttribute(HOST_ID) == null;
  }

  @Override
  public int order() {
    // Run after cloud provider resource providers
    return Integer.MAX_VALUE - 1;
  }
}
