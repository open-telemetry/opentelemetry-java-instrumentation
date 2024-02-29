/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ResourceProvider} for automatically configuring <code>host.id</code> according to <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/host.md#non-privileged-machine-id-lookup">the
 * semantic conventions</a>
 */
public final class HostIdResourceProvider implements ConditionalResourceProvider {

  private static final Logger logger = Logger.getLogger(HostIdResourceProvider.class.getName());

  public static final String REGISTRY_QUERY =
      "reg query HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography /v MachineGuid";

  private final Supplier<OsType> getOsType;

  private final Function<Path, List<String>> machineIdReader;

  private final Supplier<List<String>> queryWindowsRegistry;

  enum OsType {
    WINDOWS,
    LINUX,
    OTHER
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
        HostIdResourceProvider::readMachineIdFile,
        HostIdResourceProvider::queryWindowsRegistry);
  }

  // Visible for testing

  HostIdResourceProvider(
      Supplier<OsType> getOsType,
      Function<Path, List<String>> machineIdReader,
      Supplier<List<String>> queryWindowsRegistry) {
    this.getOsType = getOsType;
    this.machineIdReader = machineIdReader;
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
      case OTHER:
        break;
    }
    logger.fine("Unsupported OS type: " + osType);
    return Resource.empty();
  }

  private Resource readLinuxMachineId() {
    Path path = FileSystems.getDefault().getPath("/etc/machine-id");
    List<String> lines = machineIdReader.apply(path);
    if (lines.isEmpty()) {
      return Resource.empty();
    }
    return Resource.create(Attributes.of(ResourceAttributes.HOST_ID, lines.get(0)));
  }

  private static List<String> readMachineIdFile(Path path) {
    try {
      List<String> lines = Files.readAllLines(path);
      if (lines.isEmpty()) {
        logger.fine("Failed to read /etc/machine-id: empty file");
      }
      return lines;
    } catch (IOException e) {
      logger.log(Level.FINE, "Failed to read /etc/machine-id", e);
      return Collections.emptyList();
    }
  }

  private static OsType getOsType() {
    // see
    // https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/SystemUtils.java
    // for values
    String osName = System.getProperty("os.name");
    if (osName == null) {
      return OsType.OTHER;
    }
    if (osName.startsWith("Windows")) {
      return OsType.WINDOWS;
    }
    if (osName.toLowerCase(Locale.ROOT).equals("linux")) {
      return OsType.LINUX;
    }
    return OsType.OTHER;
  }

  private Resource readWindowsGuid() {
    List<String> lines = queryWindowsRegistry.get();

    for (String line : lines) {
      if (line.contains("MachineGuid")) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 3) {
          return Resource.create(Attributes.of(ResourceAttributes.HOST_ID, parts[2]));
        }
      }
    }
    logger.fine("Failed to read Windows registry: No MachineGuid found in output: " + lines);
    return Resource.empty();
  }

  private static List<String> queryWindowsRegistry() {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", REGISTRY_QUERY);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();

      List<String> output = getProcessOutput(process);

      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroy();
        logger.fine("Timed out waiting for reg query to complete");
        return Collections.emptyList();
      }

      int exitedValue = process.exitValue();
      if (exitedValue != 0) {
        logger.fine(
            "Failed to read Windows registry. Exit code: "
                + exitedValue
                + " Output: "
                + String.join("\n", output));

        return Collections.emptyList();
      }

      return output;
    } catch (IOException | InterruptedException e) {
      logger.log(Level.FINE, "Failed to read Windows registry", e);
      return Collections.emptyList();
    }
  }

  public static List<String> getProcessOutput(Process process)
      throws IOException, InterruptedException {
    List<String> result = new ArrayList<>();

    try (BufferedReader processOutputReader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String readLine;

      while ((readLine = processOutputReader.readLine()) != null) {
        result.add(readLine);
      }
    }
    return result;
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    return !config
            .getMap("otel.resource.attributes")
            .containsKey(ResourceAttributes.HOST_ID.getKey())
        && existing.getAttribute(ResourceAttributes.HOST_ID) == null;
  }

  @Override
  public int order() {
    // Run after cloud provider resource providers
    return Integer.MAX_VALUE - 1;
  }
}
