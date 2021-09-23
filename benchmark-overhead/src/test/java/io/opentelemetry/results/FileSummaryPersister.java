/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/** Writes the summary file of the last run into the results dir. */
class FileSummaryPersister implements ResultsPersister {

  private final Path file;

  public FileSummaryPersister(Path file) {
    this.file = file;
  }

  @Override
  public void write(List<AppPerfResults> results) {
    try (PrintStream out = new PrintStream(file.toFile())) {
      new PrintStreamPersister(out).write(results);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error opening output file for results", e);
    }
  }
}
