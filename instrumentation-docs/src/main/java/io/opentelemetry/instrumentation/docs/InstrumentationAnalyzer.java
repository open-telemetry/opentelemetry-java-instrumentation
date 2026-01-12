/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.internal.TelemetryMerger;
import io.opentelemetry.instrumentation.docs.parsers.EmittedScopeParser;
import io.opentelemetry.instrumentation.docs.parsers.GradleParser;
import io.opentelemetry.instrumentation.docs.parsers.MetricParser;
import io.opentelemetry.instrumentation.docs.parsers.ModuleParser;
import io.opentelemetry.instrumentation.docs.parsers.SpanParser;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
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
    InstrumentationMetadata metaData = getMetadata(module);
    if (metaData != null) {
      module.setMetadata(metaData);
    }

    module.setTargetVersions(getVersionInformation(module));

    // Handle telemetry merging (manual + emitted)
    setMergedTelemetry(module, metaData);

    InstrumentationScopeInfo scopeInfo = EmittedScopeParser.getScope(fileManager, module);
    if (scopeInfo != null) {
      module.setScopeInfo(scopeInfo);
    }
  }

  @Nullable
  private InstrumentationMetadata getMetadata(InstrumentationModule module)
      throws JsonProcessingException {
    String metadataFile = fileManager.getMetaDataFile(module.getSrcPath());
    if (metadataFile == null) {
      return null;
    }
    try {
      return YamlHelper.metaDataParser(metadataFile);
    } catch (ValueInstantiationException | MismatchedInputException e) {
      logger.severe("Error parsing metadata file for " + module.getInstrumentationName());
      throw e;
    }
  }

  private Map<InstrumentationType, Set<String>> getVersionInformation(
      InstrumentationModule module) {
    List<String> gradleFiles = fileManager.findBuildGradleFiles(module.getSrcPath());
    return GradleParser.extractVersions(gradleFiles, module);
  }

  /**
   * Sets merged telemetry data on the module, combining manual telemetry from metadata.yaml with
   * emitted telemetry from .telemetry files.
   */
  private void setMergedTelemetry(
      InstrumentationModule module, @Nullable InstrumentationMetadata metadata) throws IOException {
    Map<String, List<EmittedMetrics.Metric>> emittedMetrics =
        MetricParser.getMetrics(module, fileManager);
    Map<String, List<EmittedSpans.Span>> emittedSpans = SpanParser.getSpans(module, fileManager);

    if (metadata != null && !metadata.getAdditionalTelemetry().isEmpty()) {
      TelemetryMerger.MergedTelemetryData merged =
          TelemetryMerger.merge(
              metadata.getAdditionalTelemetry(),
              metadata.getOverrideTelemetry(),
              emittedMetrics,
              emittedSpans,
              module.getInstrumentationName());

      module.setMetrics(merged.metrics());
      module.setSpans(merged.spans());
    } else {
      // No manual telemetry, use emitted only
      module.setMetrics(emittedMetrics);
      module.setSpans(emittedSpans);
    }
  }
}
