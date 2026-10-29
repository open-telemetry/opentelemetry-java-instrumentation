/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Factory for a {@link Resource} which provides information about the host info. */
public final class HostResource {

  private static final Logger logger = Logger.getLogger(HostResource.class.getName());

  // copied from HostIncubatingAttributes
  private static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");
  private static final AttributeKey<String> HOST_ARCH = AttributeKey.stringKey("host.arch");
  private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");

  // Non-privileged machine-id sources per the semantic conventions. Commands are invoked with
  // absolute paths to avoid resolving them through a potentially attacker controlled PATH, see
  // https://github.com/open-telemetry/semantic-conventions/pull/3896
  private static final List<String> LINUX_MACHINE_ID_PATHS =
      asList("/etc/machine-id", "/var/lib/dbus/machine-id");
  private static final String BSD_HOSTID_PATH = "/etc/hostid";
  private static final List<String> BSD_KENV_COMMAND =
      unmodifiableList(asList("/bin/kenv", "-q", "smbios.system.uuid"));
  private static final List<String> MACOS_IOREG_COMMAND =
      unmodifiableList(asList("/usr/sbin/ioreg", "-rd1", "-c", "IOPlatformExpertDevice"));

  // Prefer the SystemRoot/windir environment variables to locate the Windows directory, falling
  // back to the conventional install path only if neither is set.
  private static final List<String> WINDOWS_ROOT_ENV_VARS = asList("SystemRoot", "windir");
  private static final String WINDOWS_ROOT_FALLBACK = "C:\\Windows";
  private static final List<String> WINDOWS_REGISTRY_QUERY_ARGS =
      asList("query", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid");

  // Bound how long a host id lookup command may run to avoid locking SDK startup
  private static final long COMMAND_TIMEOUT_MILLIS = 2000;

  private static final Resource INSTANCE = buildResource();

  private static final HostResource hostResource =
      new HostResource(
          HostResource::getOsTypeSystemProperty,
          HostResource::readFileLines,
          HostResource::runCommand);

  private final Supplier<String> getOsType;
  private final Function<Path, List<String>> fileReader;
  private final Function<List<String>, List<String>> commandExecutor;

  /** Returns a {@link Resource} describing the host, without host ID detection. */
  public static Resource get() {
    return INSTANCE;
  }

  /**
   * Returns a {@link Resource} describing the host, detecting {@code host.id} when {@code
   * collectHostId} is true.
   */
  public static Resource create(boolean collectHostId) {
    return hostResource.createResource(collectHostId);
  }

  // Visible for testing
  HostResource(
      Supplier<String> getOsType,
      Function<Path, List<String>> fileReader,
      Function<List<String>, List<String>> commandExecutor) {
    this.getOsType = getOsType;
    this.fileReader = fileReader;
    this.commandExecutor = commandExecutor;
  }

  Resource createResource(boolean collectHostId) {
    if (!collectHostId) {
      return get();
    }
    String hostId;
    try {
      hostId = detectHostId();
    } catch (SecurityException e) {
      logger.log(FINE, "Failed to detect host ID", e);
      return get();
    }
    return Resource.create(
        INSTANCE.getAttributes().toBuilder().put(HOST_ID, hostId).build(), SchemaUrls.V1_40_0);
  }

  // Visible for testing
  static Resource buildResource() {
    AttributesBuilder attributes = Attributes.builder();
    try {
      attributes.put(HOST_NAME, InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException ignored) {
      // Ignore
    }
    String hostArch = null;
    try {
      hostArch = System.getProperty("os.arch");
    } catch (SecurityException ignored) {
      // Ignore
    }
    attributes.put(HOST_ARCH, hostArch);

    return Resource.create(attributes.build(), SchemaUrls.V1_40_0);
  }

  @Nullable
  String detectHostId() {
    if (runningWindows()) {
      return readWindowsGuid();
    }
    if (runningLinux()) {
      return readLinuxMachineId();
    }
    if (runningMacOs()) {
      return readMacOsUuid();
    }
    if (runningBsd()) {
      return readBsdHostId();
    }
    logger.log(FINE, "Unsupported OS type: {0}", getOsType.get());
    return null;
  }

  private boolean runningLinux() {
    return getOsType.get().toLowerCase(Locale.ROOT).equals("linux");
  }

  private boolean runningWindows() {
    return getOsType.get().startsWith("Windows");
  }

  private boolean runningMacOs() {
    // os.name is "Mac OS X" on the JVM (not "Darwin" as reported by uname)
    String osType = getOsType.get().toLowerCase(Locale.ROOT);
    return osType.contains("mac") || osType.contains("darwin");
  }

  private boolean runningBsd() {
    return getOsType.get().toLowerCase(Locale.ROOT).endsWith("bsd");
  }

  // see
  // https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/SystemUtils.java
  // for values
  private static String getOsTypeSystemProperty() {
    return System.getProperty("os.name", "");
  }

  @Nullable
  private String readLinuxMachineId() {
    // /etc/machine-id is the primary source, /var/lib/dbus/machine-id is the fallback
    for (String path : LINUX_MACHINE_ID_PATHS) {
      String machineId =
          firstNonBlankLine(fileReader.apply(FileSystems.getDefault().getPath(path)));
      if (machineId != null) {
        return machineId;
      }
    }
    return null;
  }

  @Nullable
  private String readBsdHostId() {
    // /etc/hostid is the primary source, kenv is the fallback
    String hostId =
        firstNonBlankLine(fileReader.apply(FileSystems.getDefault().getPath(BSD_HOSTID_PATH)));
    if (hostId == null) {
      hostId = firstNonBlankLine(commandExecutor.apply(BSD_KENV_COMMAND));
    }
    if (hostId != null) {
      return hostId;
    }
    logger.fine("Failed to read host id on BSD: no value found in /etc/hostid or kenv output");
    return null;
  }

  @Nullable
  private String readMacOsUuid() {
    List<String> lines = commandExecutor.apply(MACOS_IOREG_COMMAND);

    for (String line : lines) {
      if (line.contains("IOPlatformUUID")) {
        // line looks like: "IOPlatformUUID" = "0123456789ABCDEF"
        int equalsIndex = line.indexOf('=');
        if (equalsIndex >= 0) {
          String uuid = line.substring(equalsIndex + 1).trim().replace("\"", "").trim();
          if (!uuid.isEmpty()) {
            return uuid;
          }
        }
      }
    }
    logger.fine("Failed to read macOS host id: no IOPlatformUUID found in ioreg output");
    return null;
  }

  @Nullable
  private String readWindowsGuid() {
    List<String> command = new ArrayList<>();
    command.add(windowsRegPath(System::getenv));
    command.addAll(WINDOWS_REGISTRY_QUERY_ARGS);
    List<String> lines = commandExecutor.apply(command);

    for (String line : lines) {
      if (line.contains("MachineGuid")) {
        // line looks like: MachineGuid    REG_SZ    0123456789ABCDEF
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 3) {
          return parts[2];
        }
      }
    }
    logger.fine("Failed to read Windows registry: No MachineGuid found in output");
    return null;
  }

  // Visible for testing
  static String windowsRegPath(Function<String, String> getEnv) {
    String root = null;
    for (String envVar : WINDOWS_ROOT_ENV_VARS) {
      root = getEnv.apply(envVar);
      if (root != null && !root.isEmpty()) {
        break;
      }
    }
    if (root == null || root.isEmpty()) {
      root = WINDOWS_ROOT_FALLBACK;
    }
    return root + "\\System32\\reg.exe";
  }

  @Nullable
  private static String firstNonBlankLine(List<String> lines) {
    for (String line : lines) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        return trimmed;
      }
    }
    return null;
  }

  private static List<String> readFileLines(Path path) {
    try {
      List<String> lines = Files.readAllLines(path);
      if (lines.isEmpty()) {
        logger.log(FINE, "Failed to read {0}: empty file", path);
      }
      return lines;
    } catch (IOException e) {
      logger.log(FINE, "Failed to read " + path, e);
      return emptyList();
    }
  }

  private static List<String> runCommand(List<String> command) {
    return runCommand(command, COMMAND_TIMEOUT_MILLIS);
  }

  // Visible for testing
  static List<String> runCommand(List<String> command, long timeoutMillis) {
    Process process = null;
    // set once this method stops caring about the result, so that the thread below can tell a
    // command that failed on its own from one that was killed on the way out
    AtomicBoolean abandoned = new AtomicBoolean();
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.redirectErrorStream(true);
      process = processBuilder.start();

      // reading the output to EOF and waiting for the process to exit are both unbounded, so they
      // run on another thread that the timeout below can walk away from. draining the output also
      // has to happen before waiting for exit, otherwise a chatty command deadlocks on a full pipe
      // buffer.
      Process startedProcess = process;
      FutureTask<List<String>> commandOutput =
          new FutureTask<>(() -> getCommandOutput(startedProcess, command, abandoned));
      Thread readerThread = new Thread(commandOutput, "otel-host-id-lookup");
      readerThread.setDaemon(true);
      readerThread.start();

      return commandOutput.get(timeoutMillis, MILLISECONDS);
    } catch (TimeoutException e) {
      logger.log(FINE, "Timed out running command {0}", command);
      return emptyList();
    } catch (IOException | ExecutionException e) {
      logger.log(FINE, "Failed to run command " + command, e);
      return emptyList();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.log(FINE, "Interrupted running command " + command, e);
      return emptyList();
    } finally {
      abandoned.set(true);
      if (process != null) {
        // no-op if the process already exited, otherwise this both stops the stray child and
        // unblocks the reader thread
        process.destroyForcibly();
      }
    }
  }

  private static List<String> getCommandOutput(
      Process process, List<String> command, AtomicBoolean abandoned)
      throws IOException, InterruptedException {
    List<String> output = getProcessOutput(process.getInputStream());

    int exitedValue = process.waitFor();
    if (abandoned.get()) {
      return emptyList();
    }

    if (exitedValue != 0) {
      logger.fine("Failed to run command " + command + ". Exit code: " + exitedValue);

      return emptyList();
    }

    return output;
  }

  private static List<String> getProcessOutput(InputStream processOutput) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput, UTF_8))) {
      return reader.lines().collect(toList());
    }
  }
}
