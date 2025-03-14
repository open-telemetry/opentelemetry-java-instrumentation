/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.VerifyException;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used in the {@link io.opentelemetry.instrumentation.testing.AgentTestRunner} to write telemetry
 * to metadata files within a `.telemetry` directory in each instrumentation module. This
 * information is then parsed and used to create the instrumentation list.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetaDataCollector {
  private static final String tmpFileLocation = ".telemetry";
  private static final Pattern pathPattern =
      Pattern.compile("(.*?/instrumentation/.*?)(/javaagent/|/library/)");

  public static void writeTelemetryToFiles(
      String path, InstrumentationScopeInfo instrumentationScope) throws IOException {
    String instrumentationPath = extractInstrumentationPath(path);
    writeScopeData(instrumentationPath, instrumentationScope);
  }

  private static String extractInstrumentationPath(String path) {
    String instrumentationPath = null;
    Matcher matcher = pathPattern.matcher(path);
    if (matcher.find()) {
      instrumentationPath = matcher.group(1);
    }

    if (instrumentationPath == null) {
      throw new IllegalArgumentException("Invalid path: " + path);
    }

    Path telemetryDir = Paths.get(instrumentationPath, tmpFileLocation);

    try {
      Files.createDirectories(telemetryDir);
    } catch (FileAlreadyExistsException e) {
      // Directory already exists, no action needed
    } catch (IOException e) {
      throw new VerifyException(e);
    }

    return instrumentationPath;
  }

  private static void writeScopeData(
      String instrumentationPath, InstrumentationScopeInfo instrumentationScope)
      throws IOException {

    if (instrumentationScope == null) {
      return;
    }

    Path outputPath = Paths.get(instrumentationPath, tmpFileLocation, "scope.yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(outputPath.toFile().toPath(), UTF_8)) {
      writer.write("scope:\n");
      writer.write("  name: " + instrumentationScope.getName() + "\n");
      writer.write("  version: " + instrumentationScope.getVersion() + "\n");
      writer.write("  schemaUrl: " + instrumentationScope.getSchemaUrl() + "\n");
      if (instrumentationScope.getAttributes() == null) {
        writer.write("  attributes: {}\n");
      } else {
        writer.write("  attributes:\n");
        instrumentationScope
            .getAttributes()
            .forEach(
                (key, value) -> {
                  try {
                    writer.write("      " + key + ": " + value + "\n");
                  } catch (IOException e) {
                    throw new VerifyException(e);
                  }
                });
      }
    }
  }

  private MetaDataCollector() {}
}
