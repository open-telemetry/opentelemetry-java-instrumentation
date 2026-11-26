/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.ALL;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionClassLoaderTest {
  private static final File AGENT_FILE = new File("/agent.jar");
  private static final Logger logger = Logger.getLogger(ExtensionClassLoader.class.getName());

  private final List<LogRecord> logs = new ArrayList<>();
  private final Handler handler =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          logs.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };
  private Level previousLevel;

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    logger.setLevel(ALL);
    logger.addHandler(handler);
  }

  @AfterEach
  void stopCapturingLogs() {
    logger.removeHandler(handler);
    logger.setLevel(previousLevel);
  }

  @Test
  void testParseLocation(@TempDir Path outputDir) throws Exception {
    String jarPath1 = createJar("test-1.jar", outputDir);
    String jarPath2 = createJar("test-2.jar", outputDir);
    // test that non-jar file is skipped
    Files.createFile(outputDir.resolve("test.txt"));
    {
      List<URL> result = ExtensionClassLoader.parseLocation(jarPath1, AGENT_FILE);
      assertThat(result.size()).isEqualTo(1);
    }
    {
      // empty paths are ignored
      List<URL> result = ExtensionClassLoader.parseLocation("," + jarPath1 + ",,,", AGENT_FILE);
      assertThat(result.size()).isEqualTo(1);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation(jarPath1 + "," + jarPath2, AGENT_FILE);
      assertThat(result.size()).isEqualTo(2);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation(outputDir.toString(), AGENT_FILE);
      assertThat(result.size()).isEqualTo(2);
    }
    {
      List<URL> result =
          ExtensionClassLoader.parseLocation(
              outputDir + "," + jarPath1 + "," + jarPath2, AGENT_FILE);
      assertThat(result.size()).isEqualTo(4);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation("/anydir", AGENT_FILE);
      assertThat(result.size()).isEqualTo(0);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation("/anyfile.jar", AGENT_FILE);
      assertThat(result.size()).isEqualTo(0);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation(jarPath1 + ",/anyfile.jar", AGENT_FILE);
      assertThat(result.size()).isEqualTo(1);
    }
  }

  @Test
  void testParseLocationWarnings(@TempDir Path outputDir) throws Exception {
    String jarPath1 = createJar("test-1.jar", outputDir);
    Path emptyDir = Files.createDirectory(outputDir.resolve("empty"));

    ExtensionClassLoader.parseLocation(jarPath1, AGENT_FILE);
    assertThat(logsAtLevel(WARNING)).isEmpty();
    assertThat(logsAtLevel(FINE)).hasSize(1);
    logs.clear();

    ExtensionClassLoader.parseLocation(outputDir.toString(), AGENT_FILE);
    assertThat(logsAtLevel(WARNING)).isEmpty();
    assertThat(logsAtLevel(FINE)).hasSize(1);
    logs.clear();

    ExtensionClassLoader.parseLocation("/does/not/exist", AGENT_FILE);
    assertThat(logsAtLevel(WARNING)).hasSize(1);
    assertThat(logsAtLevel(FINE)).isEmpty();
    logs.clear();

    ExtensionClassLoader.parseLocation(emptyDir.toString(), AGENT_FILE);
    assertThat(logsAtLevel(WARNING)).hasSize(1);
    assertThat(logsAtLevel(FINE)).isEmpty();
    logs.clear();

    ExtensionClassLoader.parseLocation(jarPath1 + ",/does/not/exist", AGENT_FILE);
    assertThat(logsAtLevel(WARNING)).hasSize(1);
    assertThat(logsAtLevel(FINE)).hasSize(1);
    logs.clear();

    ExtensionClassLoader.parseLocation(null, AGENT_FILE);
    assertThat(logsAtLevel(WARNING)).isEmpty();
    assertThat(logsAtLevel(FINE)).isEmpty();

    ExtensionClassLoader.parseLocation("", AGENT_FILE);
    assertThat(logsAtLevel(WARNING)).isEmpty();
    assertThat(logsAtLevel(FINE)).isEmpty();
  }

  private List<String> logsAtLevel(Level level) {
    List<String> result = new ArrayList<>();
    for (LogRecord record : logs) {
      if (record.getLevel().equals(level)) {
        result.add(record.getMessage());
      }
    }
    return result;
  }

  private static String createJar(String name, Path directory) throws Exception {
    Path jarPath = directory.resolve(name);
    createJar(jarPath);
    return jarPath.toAbsolutePath().toString();
  }

  private static void createJar(Path path) throws Exception {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      // empty jar
    }
  }
}
