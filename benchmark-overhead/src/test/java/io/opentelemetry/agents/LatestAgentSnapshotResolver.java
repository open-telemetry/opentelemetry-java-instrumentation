/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.agents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LatestAgentSnapshotResolver {

  private static final Logger logger = LoggerFactory.getLogger(LatestAgentSnapshotResolver.class);

  Optional<Path> resolve() throws IOException {
    Path localJavaagentPath = findLocalJavaagentJar();

    if (localJavaagentPath == null || !Files.exists(localJavaagentPath)) {
      throw new IOException("Local javaagent JAR not found. Please run './gradlew :javaagent:assemble' from the project root first.");
    }

    logger.info("Using local javaagent JAR: {}", localJavaagentPath);

    Path targetPath = Paths.get(".", "opentelemetry-javaagent-SNAPSHOT.jar");
    Files.copy(localJavaagentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    return Optional.of(targetPath);
  }

  private Path findLocalJavaagentJar() {
    Path relativePath = Paths.get("../javaagent/build/libs").toAbsolutePath().normalize();
    Path javaagentJar = findJavaagentJarInDirectory(relativePath);

    if (javaagentJar != null) {
      return javaagentJar;
    }

    // If not found, try from the project root (in case running from different location)
    Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
    while (projectRoot.getParent() != null) {
      Path gradlewFile = projectRoot.resolve("gradlew");
      if (Files.exists(gradlewFile)) {
        // Found the project root
        Path javaagentLibsDir = projectRoot.resolve("javaagent/build/libs");
        javaagentJar = findJavaagentJarInDirectory(javaagentLibsDir);
        if (javaagentJar != null) {
          return javaagentJar;
        }
        break;
      }
      projectRoot = projectRoot.getParent();
    }

    return null;
  }

  private Path findJavaagentJarInDirectory(Path directory) {
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
      return null;
    }

    try {
      return Files.list(directory)
          .filter(path -> {
            String filename = path.getFileName().toString();
            // Look for the main jar: opentelemetry-javaagent-VERSION.jar (no additional suffixes)
            return filename.startsWith("opentelemetry-javaagent-") &&
                   filename.endsWith(".jar") &&
                   !filename.matches(".*-[a-z]+\\.jar"); // excludes anything with -word.jar pattern
          })
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      logger.warn("Failed to list files in directory {}: {}", directory, e.getMessage());
      return null;
    }
  }
}

