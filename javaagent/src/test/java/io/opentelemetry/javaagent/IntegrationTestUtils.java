/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntegrationTestUtils {

  private static final Logger logger = LoggerFactory.getLogger(IntegrationTestUtils.class);

  /** Returns the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return getAgentFieldClassloader("agentClassLoader");
  }

  private static ClassLoader getAgentFieldClassloader(String fieldName) {
    Field classloaderField = null;
    try {
      Class<?> agentClass =
          ClassLoader.getSystemClassLoader()
              .loadClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer");
      classloaderField = agentClass.getDeclaredField(fieldName);
      classloaderField.setAccessible(true);
      return (ClassLoader) classloaderField.get(null);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      if (null != classloaderField) {
        classloaderField.setAccessible(false);
      }
    }
  }

  // TODO this works only accidentally now, because we don't have extensions in tests.
  /** Returns the URL to the jar the agent appended to the bootstrap classpath. */
  public static ClassLoader getBootstrapProxy() throws Exception {
    ClassLoader agentClassLoader = getAgentClassLoader();
    Method getBootstrapProxy = agentClassLoader.getClass().getMethod("getBootstrapProxy");
    return (ClassLoader) getBootstrapProxy.invoke(agentClassLoader);
  }

  /** See {@link IntegrationTestUtils#createJarWithClasses(String, Class[])}. */
  public static URL createJarWithClasses(Class<?>... classes) throws IOException {
    return createJarWithClasses(null, classes);
  }

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param mainClassname The name of the class to use for Main-Class and Premain-Class. May be null
   * @param classes classes to package into the jar.
   * @return the location of the newly created jar.
   */
  public static URL createJarWithClasses(String mainClassname, Class<?>... classes)
      throws IOException {
    File tmpJar = File.createTempFile(UUID.randomUUID() + "-", ".jar");
    tmpJar.deleteOnExit();

    Manifest manifest = new Manifest();
    if (mainClassname != null) {
      Attributes mainAttributes = manifest.getMainAttributes();
      mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClassname);
    }
    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (Class<?> clazz : classes) {
      addToJar(clazz, target);
    }
    target.close();

    return tmpJar.toURI().toURL();
  }

  private static void addToJar(Class<?> clazz, JarOutputStream jarOutputStream) throws IOException {
    InputStream inputStream = null;
    ClassLoader loader = clazz.getClassLoader();
    if (null == loader) {
      // bootstrap resources can be fetched through the system loader
      loader = ClassLoader.getSystemClassLoader();
    }
    try {
      JarEntry entry = new JarEntry(getResourceName(clazz.getName()));
      jarOutputStream.putNextEntry(entry);
      inputStream = loader.getResourceAsStream(getResourceName(clazz.getName()));

      byte[] buffer = new byte[1024];
      while (true) {
        int count = inputStream.read(buffer);
        if (count == -1) {
          break;
        }
        jarOutputStream.write(buffer, 0, count);
      }
      jarOutputStream.closeEntry();
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  // com.foo.Bar -> com/foo/Bar.class
  public static String getResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

  @SuppressWarnings("unchecked")
  public static List<String> getBootstrapPackagePrefixes() throws Exception {
    Field f =
        getAgentClassLoader()
            .loadClass("io.opentelemetry.javaagent.tooling.Constants")
            .getField("BOOTSTRAP_PACKAGE_PREFIXES");
    return (List<String>) f.get(null);
  }

  private static String getAgentArgument() {
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    for (String arg : runtimeMxBean.getInputArguments()) {
      if (arg.startsWith("-javaagent")) {
        return arg;
      }
    }

    throw new IllegalStateException("Agent jar not found");
  }

  public static int runOnSeparateJvm(
      String mainClassName,
      String[] jvmArgs,
      String[] mainMethodArgs,
      Map<String, String> envVars,
      boolean printOutputStreams)
      throws Exception {
    String classPath = System.getProperty("java.class.path");
    return runOnSeparateJvm(
        mainClassName, jvmArgs, mainMethodArgs, envVars, classPath, printOutputStreams);
  }

  /**
   * On a separate JVM, run the main method for a given class.
   *
   * @param mainClassName The name of the entry point class. Must declare a main method.
   * @param printOutputStreams if true, print stdout and stderr of the child jvm
   * @return the return code of the child jvm
   */
  public static int runOnSeparateJvm(
      String mainClassName,
      String[] jvmArgs,
      String[] mainMethodArgs,
      Map<String, String> envVars,
      String classpath,
      boolean printOutputStreams)
      throws Exception {

    String separator = System.getProperty("file.separator");
    String path = System.getProperty("java.home") + separator + "bin" + separator + "java";

    List<String> vmArgsList = new ArrayList<>(Arrays.asList(jvmArgs));
    vmArgsList.add(getAgentArgument());

    List<String> commands = new ArrayList<>();
    commands.add(path);
    commands.addAll(vmArgsList);
    commands.add("-cp");
    commands.add(classpath);
    commands.add(mainClassName);
    commands.addAll(Arrays.asList(mainMethodArgs));
    ProcessBuilder processBuilder = new ProcessBuilder(commands.toArray(new String[0]));
    processBuilder.environment().putAll(envVars);

    Process process = processBuilder.start();

    StreamGobbler errorGobbler =
        new StreamGobbler(process.getErrorStream(), "ERROR", printOutputStreams);
    StreamGobbler outputGobbler =
        new StreamGobbler(process.getInputStream(), "OUTPUT", printOutputStreams);
    outputGobbler.start();
    errorGobbler.start();

    waitFor(process, 30, TimeUnit.SECONDS);

    outputGobbler.join();
    errorGobbler.join();

    return process.exitValue();
  }

  private static void waitFor(Process process, long timeout, TimeUnit unit)
      throws InterruptedException, TimeoutException {
    long startTime = System.nanoTime();
    long rem = unit.toNanos(timeout);

    do {
      try {
        process.exitValue();
        return;
      } catch (IllegalThreadStateException ex) {
        if (rem > 0) {
          Thread.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
        }
      }
      rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
    } while (rem > 0);
    throw new TimeoutException();
  }

  private static class StreamGobbler extends Thread {
    final InputStream stream;
    final String type;
    final boolean print;

    private StreamGobbler(InputStream stream, String type, boolean print) {
      this.stream = stream;
      this.type = type;
      this.print = print;
    }

    @Override
    public void run() {
      try {
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line = null;
        while ((line = reader.readLine()) != null) {
          if (print) {
            logger.info("{}> {}", type, line);
          }
        }
      } catch (IOException e) {
        logger.warn("Error gobbling.", e);
      }
    }
  }
}
