/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.util.Locale.Category.FORMAT;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DocGeneratorApplication {

  private static final Logger logger = Logger.getLogger(DocGeneratorApplication.class.getName());

  public static void main(String[] args) throws IOException {
    // Identify path to repo so we can use absolute paths
    String baseRepoPath = System.getProperty("basePath");
    if (baseRepoPath == null) {
      baseRepoPath = "./";
    } else {
      baseRepoPath += "/";
    }

    FileManager fileManager = new FileManager(baseRepoPath);
    List<InstrumentationModule> modules = new InstrumentationAnalyzer(fileManager).analyze();

    try (BufferedWriter writer =
        Files.newBufferedWriter(Paths.get(baseRepoPath + "docs/instrumentation-list.yaml"))) {
      writer.write("# This file is generated and should not be manually edited.\n");
      writer.write("# The structure and contents are a work in progress and subject to change.\n");
      writer.write(
          "# For more information see: https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13468\n\n");
      YamlHelper.generateInstrumentationYaml(modules, writer);
    }

    printStats(modules);
  }

  private static void printStats(List<InstrumentationModule> modules) {
    List<InstrumentationModule> metadata =
        modules.stream().filter(m -> m.getMetadata() != null).toList();

    long withDescriptions =
        metadata.stream()
            .filter(
                m ->
                    m.getMetadata().getDescription() != null
                        && !m.getMetadata().getDescription().isEmpty())
            .count();

    long withConfigurations =
        metadata.stream().filter(m -> !m.getMetadata().getConfigurations().isEmpty()).count();

    String stats =
        String.format(
            Locale.getDefault(FORMAT),
            """
            -----------------------------------
            Analysis Summary:
            Total Modules: %d
            By classification:
            %s
            metadata.yaml contents:
              %s
              %s
            """,
            modules.size(),
            getClassificationStats(modules),
            getPercentage("descriptions", withDescriptions, modules.size()),
            getPercentage("configurations", withConfigurations, modules.size()));

    logger.info(stats);
  }

  private static String getClassificationStats(List<InstrumentationModule> modules) {
    return modules.stream()
        .collect(
            Collectors.groupingBy(
                m -> m.getMetadata().getClassification(), TreeMap::new, Collectors.toList()))
        .entrySet()
        .stream()
        .map(
            entry ->
                String.format(
                    Locale.getDefault(FORMAT), "\t%s: %d", entry.getKey(), entry.getValue().size()))
        .collect(Collectors.joining("\n"));
  }

  private static String getPercentage(String label, long numerator, long denominator) {
    return label
        + ": "
        + numerator
        + " ("
        + String.format(Locale.getDefault(FORMAT), "%.2f", (double) numerator / denominator * 100)
        + "%)";
  }

  @SuppressWarnings("unused") // temporary helper method used for project tracking
  private static String listAllModules(List<InstrumentationModule> modules) {
    return modules.stream()
        .map(InstrumentationModule::getInstrumentationName)
        .sorted()
        .map(name -> "- [ ] " + name)
        .collect(Collectors.joining("\n"));
  }

  @SuppressWarnings("unused") // temporary helper method used for project tracking
  private static String modulesWithDescriptions(List<InstrumentationModule> modules) {
    // checklist of all modules sorted by name, with a check if description is set
    return modules.stream()
        .sorted(Comparator.comparing(InstrumentationModule::getInstrumentationName))
        .map(
            module -> {
              boolean hasDescription =
                  module.getMetadata() != null
                      && module.getMetadata().getDescription() != null
                      && !module.getMetadata().getDescription().isEmpty();
              String checkbox = hasDescription ? "- [x] " : "- [ ] ";
              return checkbox + module.getInstrumentationName();
            })
        .collect(Collectors.joining("\n"));
  }

  @SuppressWarnings("unused") // temporary helper method used for project tracking
  private static String modulesWithConfigs(List<InstrumentationModule> modules) {
    // checklist of all modules sorted by name, with a check if config is set
    return modules.stream()
        .sorted(Comparator.comparing(InstrumentationModule::getInstrumentationName))
        .map(
            module -> {
              boolean hasDescription =
                  module.getMetadata() != null
                      && module.getMetadata().getConfigurations() != null
                      && !module.getMetadata().getConfigurations().isEmpty();
              String checkbox = hasDescription ? "- [x] " : "- [ ] ";
              return checkbox + module.getInstrumentationName();
            })
        .collect(Collectors.joining("\n"));
  }

  private DocGeneratorApplication() {}
}
