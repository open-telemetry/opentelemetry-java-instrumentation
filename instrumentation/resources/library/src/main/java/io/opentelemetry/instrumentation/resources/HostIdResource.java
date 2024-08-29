/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * {@link ResourceProvider} for automatically configuring <code>host.id</code> according to <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/host.md#non-privileged-machine-id-lookup">the
 * semantic conventions</a>
 */
public final class HostIdResource {

  private static final Logger logger = Logger.getLogger(HostIdResource.class.getName());

  // copied from HostIncubatingAttributes
  static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");

  public static final String REGISTRY_QUERY =
      "reg query HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography /v MachineGuid";

  private static final HostIdResource INSTANCE =
      new HostIdResource(
          HostIdResource::getOsTypeSystemProperty,
          HostIdResource::readMachineIdFile,
          HostIdResource::queryWindowsRegistry);

  private final Supplier<String> getOsType;
  private final Function<Path, List<String>> machineIdReader;
  private final Supplier<List<String>> queryWindowsRegistry;

  // Visible for testing
  HostIdResource(
      Supplier<String> getOsType,
      Function<Path, List<String>> machineIdReader,
      Supplier<List<String>> queryWindowsRegistry) {
    this.getOsType = getOsType;
    this.machineIdReader = machineIdReader;
    this.queryWindowsRegistry = queryWindowsRegistry;
  }

  /** Returns a {@link Resource} containing the {@code host.id} resource attribute. */
  public static Resource get() {
    return INSTANCE.createResource();
  }

  /** Returns a {@link Resource} containing the {@code host.id} resource attribute. */
  Resource createResource() {
    if (runningWindows()) {
      return readWindowsGuid();
    }
    if (runningLinux()) {
      return readLinuxMachineId();
    }
    logger.log(FINE, "Unsupported OS type: {0}", getOsType.get());
    return Resource.empty();
  }

  private boolean runningLinux() {
    return getOsType.get().toLowerCase(Locale.ROOT).equals("linux");
  }

  private boolean runningWindows() {
    return getOsType.get().startsWith("Windows");
  }

  // see
  // https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/SystemUtils.java
  // for values
  private static String getOsTypeSystemProperty() {
    return System.getProperty("os.name", "");
  }

  private Resource readLinuxMachineId() {
    Path path = FileSystems.getDefault().getPath("/etc/machine-id");
    List<String> lines = machineIdReader.apply(path);
    if (lines.isEmpty()) {
      return Resource.empty();
    }
    return Resource.create(Attributes.of(HOST_ID, lines.get(0)));
  }

  private static List<String> readMachineIdFile(Path path) {
    try {
      List<String> lines = Files.readAllLines(path);
      if (lines.isEmpty()) {
        logger.fine("Failed to read /etc/machine-id: empty file");
      }
      return lines;
    } catch (IOException e) {
      logger.log(FINE, "Failed to read /etc/machine-id", e);
      return Collections.emptyList();
    }
  }

  private Resource readWindowsGuid() {
    List<String> lines = queryWindowsRegistry.get();

    for (String line : lines) {
      if (line.contains("MachineGuid")) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 3) {
          return Resource.create(Attributes.of(HOST_ID, parts[2]));
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
      int exitedValue = process.waitFor();
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
      logger.log(FINE, "Failed to read Windows registry", e);
      return Collections.emptyList();
    }
  }

  private static List<String> getProcessOutput(Process process) throws IOException {
    List<String> result = new ArrayList<>();

    try (BufferedReader processOutputReader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String readLine;

      while ((readLine = processOutputReader.readLine()) != null) {
        result.add(readLine);
      }
    }
    return result;
  }
}
