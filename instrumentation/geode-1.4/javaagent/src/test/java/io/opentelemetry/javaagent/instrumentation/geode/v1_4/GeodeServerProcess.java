/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

final class GeodeServerProcess implements AutoCloseable {
  private static final long STARTUP_TIMEOUT_SECONDS = 30;
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

  private final Process process;
  private final OutputStream childStdin;
  private final Path logFile;
  private final int port;

  static GeodeServerProcess start(Path tempDir) {
    Path workingDirectory = tempDir.toAbsolutePath();
    Path classpathJar = workingDirectory.resolve("geode-server-classpath.jar");
    Path portFile = workingDirectory.resolve("geode-server.port");
    Path logFile = workingDirectory.resolve("geode-server.log");
    Process process = null;

    try {
      createClasspathJar(classpathJar);
      String javaPath =
          System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      List<String> command = new ArrayList<>();
      command.add(javaPath);
      command.add("-Xmx256m");
      if (!System.getProperty("java.specification.version").startsWith("1.")) {
        command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
      }
      command.add("-cp");
      command.add(classpathJar.getFileName().toString());
      command.add(GeodeServerMain.class.getName());
      command.add(portFile.getFileName().toString());
      process =
          new ProcessBuilder(command)
              .directory(workingDirectory.toFile())
              .redirectErrorStream(true)
              .redirectOutput(logFile.toFile())
              .start();

      int port = waitForPort(process, portFile);
      return new GeodeServerProcess(process, logFile, port);
    } catch (InterruptedException e) {
      boolean terminated = process == null || terminateAfterStartupFailure(process);
      Thread.currentThread().interrupt();
      throw failure(
          terminated
              ? "Interrupted while starting the Geode server"
              : "Interrupted while starting the Geode server; child did not terminate",
          logFile,
          e);
    } catch (Exception e) {
      boolean terminated = process == null || terminateAfterStartupFailure(process);
      throw failure(
          terminated
              ? "Failed to start the Geode server"
              : "Failed to start the Geode server; child did not terminate",
          logFile,
          e);
    }
  }

  private GeodeServerProcess(Process process, Path logFile, int port) {
    this.process = process;
    childStdin = process.getOutputStream();
    this.logFile = logFile;
    this.port = port;
  }

  int getPort() {
    return port;
  }

  @Override
  public void close() {
    IOException stdinFailure = null;
    try {
      childStdin.close();
    } catch (IOException e) {
      stdinFailure = e;
    }

    try {
      if (!waitForExit(process, SHUTDOWN_TIMEOUT_SECONDS)) {
        process.destroy();
        if (!waitForExit(process, SHUTDOWN_TIMEOUT_SECONDS)) {
          process.destroyForcibly();
          if (!waitForExit(process, SHUTDOWN_TIMEOUT_SECONDS)) {
            throw failure("Geode server did not terminate", logFile, null);
          }
        }
      }
    } catch (InterruptedException e) {
      process.destroyForcibly();
      boolean terminated = waitForExitUninterruptibly(process, SHUTDOWN_TIMEOUT_SECONDS);
      Thread.currentThread().interrupt();
      throw failure(
          terminated
              ? "Interrupted while stopping the Geode server"
              : "Interrupted while stopping the Geode server; child did not terminate",
          logFile,
          e);
    }

    if (stdinFailure != null) {
      throw failure("Failed to close the Geode server stdin", logFile, stdinFailure);
    }
    if (process.exitValue() != 0) {
      throw failure("Geode server exited with code " + process.exitValue(), logFile, null);
    }
  }

  private static void createClasspathJar(Path classpathJar) throws IOException {
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.put(Attributes.Name.CLASS_PATH, manifestClasspath());

    try (OutputStream output = Files.newOutputStream(classpathJar);
        JarOutputStream ignored = new JarOutputStream(output, manifest)) {}
  }

  private static String manifestClasspath() {
    String[] entries =
        System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator), -1);
    StringBuilder classpath = new StringBuilder();
    for (String entry : entries) {
      if (classpath.length() > 0) {
        classpath.append(' ');
      }
      File file = entry.isEmpty() ? new File(".") : new File(entry);
      classpath.append(file.getAbsoluteFile().toURI().toASCIIString());
    }
    return classpath.toString();
  }

  private static int waitForPort(Process process, Path portFile)
      throws IOException, InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(STARTUP_TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException(
            "Geode server exited with code " + process.exitValue() + " before becoming ready");
      }
      if (Files.exists(portFile)) {
        int port = Integer.parseInt(new String(Files.readAllBytes(portFile), UTF_8).trim());
        if (port < 1 || port > 65535) {
          throw new IllegalStateException("Geode server published invalid port " + port);
        }
        if (!process.isAlive()) {
          throw new IllegalStateException(
              "Geode server exited with code " + process.exitValue() + " after becoming ready");
        }
        return port;
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException(
        "Timed out waiting " + STARTUP_TIMEOUT_SECONDS + " seconds for the Geode server");
  }

  private static boolean waitForExit(Process process, long timeoutSeconds)
      throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(timeoutSeconds);
    while (process.isAlive()) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0 || !process.waitFor(remaining, NANOSECONDS)) {
        return false;
      }
    }
    return true;
  }

  private static boolean terminateAfterStartupFailure(Process process) {
    process.destroy();
    if (waitForExitUninterruptibly(process, SHUTDOWN_TIMEOUT_SECONDS)) {
      return true;
    }
    process.destroyForcibly();
    return waitForExitUninterruptibly(process, SHUTDOWN_TIMEOUT_SECONDS);
  }

  private static boolean waitForExitUninterruptibly(Process process, long timeoutSeconds) {
    long deadline = System.nanoTime() + SECONDS.toNanos(timeoutSeconds);
    boolean interrupted = false;
    try {
      while (process.isAlive()) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return false;
        }
        try {
          if (!process.waitFor(remaining, NANOSECONDS)) {
            return false;
          }
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      return true;
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static IllegalStateException failure(String message, Path logFile, Throwable cause) {
    String log;
    try {
      log =
          Files.exists(logFile) ? new String(Files.readAllBytes(logFile), UTF_8) : "<not created>";
    } catch (IOException e) {
      log = "<failed to read " + logFile + ": " + e.getMessage() + ">";
    }

    String detail =
        message
            + System.lineSeparator()
            + "Child log ("
            + logFile
            + "):"
            + System.lineSeparator()
            + log;
    return cause == null
        ? new IllegalStateException(detail)
        : new IllegalStateException(detail, cause);
  }
}
