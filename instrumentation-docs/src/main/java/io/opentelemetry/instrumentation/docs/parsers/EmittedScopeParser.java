/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import io.opentelemetry.instrumentation.docs.internal.EmittedScope;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.InstrumentationScopeInfoBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * This class is responsible for parsing scope files from the `.telemetry` directory of an
 * instrumentation module and collecting all unique scopes.
 */
public class EmittedScopeParser {
  private static final Logger logger = Logger.getLogger(EmittedScopeParser.class.getName());

  @Nullable
  public static InstrumentationScopeInfo getScope(
      FileManager fileManager, InstrumentationModule module) {
    Set<EmittedScope.Scope> scopes =
        EmittedScopeParser.getScopesFromFiles(fileManager.rootDir(), module.getSrcPath());
    if (scopes.isEmpty()) {
      return null;
    }

    EmittedScope.Scope scope =
        scopes.stream()
            .filter(
                item ->
                    item.getName() != null
                        && item.getName().contains(module.getInstrumentationName()))
            .findFirst()
            .orElse(null);
    if (scope == null) {
      return null;
    }

    String instrumentationName = "io.opentelemetry." + module.getInstrumentationName();
    InstrumentationScopeInfoBuilder builder = InstrumentationScopeInfo.builder(instrumentationName);

    // This will identify any module that might deviate from the standard naming convention
    if (scope.getName() != null && !scope.getName().equals(instrumentationName)) {
      logger.severe(
          "Scope name mismatch. Expected: " + instrumentationName + ", got: " + scope.getName());
    }

    if (scope.getSchemaUrl() != null) {
      builder.setSchemaUrl(scope.getSchemaUrl());
    }
    if (scope.getAttributes() != null) {
      builder.setAttributes(scope.getAttributes());
    }

    return builder.build();
  }

  /**
   * Looks for scope files in the .telemetry directory and collects all unique scopes.
   *
   * @param rootDir the root directory
   * @param instrumentationDirectory the instrumentation directory relative to root
   * @return set of all unique scopes found in scope files
   */
  public static Set<EmittedScope.Scope> getScopesFromFiles(
      String rootDir, String instrumentationDirectory) {
    Path telemetryDir = Paths.get(rootDir + "/" + instrumentationDirectory, ".telemetry");

    return parseAllScopeFiles(telemetryDir);
  }

  /**
   * Parses all scope-*.yaml files in the .telemetry directory and collects all unique scopes.
   *
   * @param telemetryDir the path to the .telemetry directory
   * @return a set of unique scopes
   */
  private static Set<EmittedScope.Scope> parseAllScopeFiles(Path telemetryDir) {
    Set<EmittedScope.Scope> allScopes = new HashSet<>();

    if (Files.exists(telemetryDir) && Files.isDirectory(telemetryDir)) {
      try (Stream<Path> files = Files.list(telemetryDir)) {
        files
            .filter(path -> path.getFileName().toString().startsWith("scope-"))
            .forEach(
                path -> {
                  String content = FileManager.readFileToString(path.toString());
                  if (content != null) {
                    EmittedScope parsed;
                    try {
                      parsed = YamlHelper.emittedScopeParser(content);
                    } catch (RuntimeException e) {
                      logger.severe("Error parsing scope file (" + path + "): " + e.getMessage());
                      return;
                    }

                    List<EmittedScope.Scope> scopes = parsed.getScopes();
                    if (scopes != null) {
                      allScopes.addAll(scopes);
                    }
                  }
                });
      } catch (IOException e) {
        logger.severe("Error reading scope files: " + e.getMessage());
      }
    }
    return allScopes;
  }

  private EmittedScopeParser() {}
}
