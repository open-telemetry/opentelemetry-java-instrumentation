/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import io.opentelemetry.config.TestConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainResultsPersister implements ResultsPersister {

  private final TestConfig config;
  private final Path resultsDir;

  public MainResultsPersister(TestConfig config) {
    this(config, Paths.get("results"));
  }

  MainResultsPersister(TestConfig config, Path resultsDir) {
    this.config = config;
    this.resultsDir = resultsDir;
  }

  @Override
  public void write(List<AppPerfResults> results) {
    Path outputDir = resultsDir.resolve("petclinic-v2").resolve(config.getName());
    ensureCreated(outputDir);
    new ConsoleResultsPersister().write(results);
    new FileSummaryPersister(outputDir.resolve("summary.txt")).write(results);
    new CsvPersister(outputDir.resolve("results.csv")).write(results);
  }

  private void ensureCreated(Path outputDir) {
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new RuntimeException("Error creating output directory", e);
    }
  }
}
