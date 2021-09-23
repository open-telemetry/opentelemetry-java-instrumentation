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

  public MainResultsPersister(TestConfig config) {
    this.config = config;
  }

  @Override
  public void write(List<AppPerfResults> results) {
    Path outputDir = Paths.get("results", config.getName());
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
