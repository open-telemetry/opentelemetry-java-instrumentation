/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetaData;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.parsers.GradleParser;
import io.opentelemetry.instrumentation.docs.parsers.MetricParser;
import io.opentelemetry.instrumentation.docs.parsers.ModuleParser;
import io.opentelemetry.instrumentation.docs.parsers.SpanParser;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Analyzes instrumentation modules by extracting version information, metrics, spans, and metadata
 * from various source files.
 */
class InstrumentationAnalyzer {

  private static final Logger logger = Logger.getLogger(InstrumentationAnalyzer.class.getName());

  private final FileManager fileManager;

  InstrumentationAnalyzer(FileManager fileManager) {
    this.fileManager = fileManager;
  }

  /**
   * Analyzes all instrumentation modules found in the root directory.
   *
   * @return a list of analyzed {@link InstrumentationModule}
   * @throws IOException if file operations fail
   */
  public List<InstrumentationModule> analyze() throws IOException {
    List<InstrumentationPath> paths = fileManager.getInstrumentationPaths();
    List<InstrumentationModule> modules =
        ModuleParser.convertToModules(fileManager.rootDir(), paths);

    for (InstrumentationModule module : modules) {
      enrichModule(module);
    }

    return modules;
  }

  private void enrichModule(InstrumentationModule module) throws IOException {
    InstrumentationMetaData metaData = getMetadata(module);
    if (metaData != null) {
      module.setMetadata(metaData);
    }

    module.setTargetVersions(getVersionInformation(module));
    module.setMetrics(MetricParser.getMetrics(module, fileManager));
    module.setSpans(SpanParser.getSpans(module, fileManager));
  }

  @Nullable
  private InstrumentationMetaData getMetadata(InstrumentationModule module)
      throws JsonProcessingException {
    String metadataFile = fileManager.getMetaDataFile(module.getSrcPath());
    if (metadataFile == null) {
      return null;
    }
    try {
      return YamlHelper.metaDataParser(metadataFile);
    } catch (ValueInstantiationException e) {
      logger.severe("Error parsing metadata file for " + module.getInstrumentationName());
      throw e;
    }
  }

  private Map<InstrumentationType, Set<String>> getVersionInformation(
      InstrumentationModule module) {
    List<String> gradleFiles = fileManager.findBuildGradleFiles(module.getSrcPath());
    return GradleParser.extractVersions(gradleFiles, module);
  }
}
