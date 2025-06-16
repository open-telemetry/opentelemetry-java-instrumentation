/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.parsers.GradleParser.parseGradleFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.opentelemetry.instrumentation.docs.internal.DependencyInfo;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.parsers.MetricParser;
import io.opentelemetry.instrumentation.docs.parsers.SpanParser;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

class InstrumentationAnalyzer {

  private static final Logger logger = Logger.getLogger(InstrumentationAnalyzer.class.getName());

  private final FileManager fileManager;

  InstrumentationAnalyzer(FileManager fileManager) {
    this.fileManager = fileManager;
  }

  /**
   * Converts a list of {@link InstrumentationPath} into a list of {@link InstrumentationModule},
   *
   * @param paths the list of {@link InstrumentationPath} objects to be converted
   * @return a list of {@link InstrumentationModule} objects with aggregated types
   */
  public static List<InstrumentationModule> convertToInstrumentationModules(
      String rootPath, List<InstrumentationPath> paths) {
    Map<String, InstrumentationModule> moduleMap = new HashMap<>();

    for (InstrumentationPath path : paths) {
      String key = path.group() + ":" + path.namespace() + ":" + path.instrumentationName();
      if (!moduleMap.containsKey(key)) {
        moduleMap.put(
            key,
            new InstrumentationModule.Builder()
                .srcPath(sanitizePathName(rootPath, path.srcPath()))
                .instrumentationName(path.instrumentationName())
                .namespace(path.namespace())
                .group(path.group())
                .build());
      }
    }

    return new ArrayList<>(moduleMap.values());
  }

  private static String sanitizePathName(String rootPath, String path) {
    return path.replace(rootPath, "").replace("/javaagent", "").replace("/library", "");
  }

  /**
   * Traverses the given root directory to find all instrumentation paths and then analyzes them.
   * Extracts version information from each instrumentation's build.gradle file, metric data from
   * files in the .telemetry directories, and other information from metadata.yaml files.
   *
   * @return a list of {@link InstrumentationModule}
   */
  List<InstrumentationModule> analyze() throws IOException {
    List<InstrumentationPath> paths = fileManager.getInstrumentationPaths();
    List<InstrumentationModule> modules =
        convertToInstrumentationModules(fileManager.rootDir(), paths);

    for (InstrumentationModule module : modules) {
      List<String> gradleFiles = fileManager.findBuildGradleFiles(module.getSrcPath());
      analyzeVersions(gradleFiles, module);

      String metadataFile = fileManager.getMetaDataFile(module.getSrcPath());
      if (metadataFile != null) {
        try {
          module.setMetadata(YamlHelper.metaDataParser(metadataFile));
        } catch (ValueInstantiationException e) {
          logger.severe("Error parsing metadata file for " + module.getInstrumentationName());
          throw e;
        }
      }

      processMetrics(module);
      processSpans(module);
    }
    return modules;
  }

  private void processMetrics(InstrumentationModule module) {
    Map<String, EmittedMetrics> metrics =
        MetricParser.getMetricsFromFiles(fileManager.rootDir(), module.getSrcPath());
    for (Map.Entry<String, EmittedMetrics> entry : metrics.entrySet()) {
      if (entry.getValue() == null || entry.getValue().getMetrics() == null) {
        continue;
      }
      module.getMetrics().put(entry.getKey(), entry.getValue().getMetrics());
    }
  }

  private void processSpans(InstrumentationModule module) throws JsonProcessingException {
    Map<String, EmittedSpans> spans =
        SpanParser.getSpansByScopeFromFiles(fileManager.rootDir(), module.getSrcPath());
    if (!spans.isEmpty()) {
      Map<String, List<EmittedSpans.Span>> filtered = filterSpansByScope(spans, module);
      module.setSpans(filtered);
    }
  }

  Map<String, List<EmittedSpans.Span>> filterSpansByScope(
      Map<String, EmittedSpans> spansByScope, InstrumentationModule module) {

    Map<String, Map<String, Set<TelemetryAttribute>>> raw = new HashMap<>();

    for (Map.Entry<String, EmittedSpans> entry : spansByScope.entrySet()) {
      if (entry.getValue() == null || entry.getValue().getSpansByScope() == null) {
        continue;
      }

      String when = entry.getValue().getWhen();
      Map<String, Set<TelemetryAttribute>> kind = raw.computeIfAbsent(when, m -> new HashMap<>());

      for (EmittedSpans.SpansByScope theseSpans : entry.getValue().getSpansByScope()) {

        if (theseSpans.getScope().equals(module.getScopeInfo().getName())) {
          for (EmittedSpans.Span span : theseSpans.getSpans()) {
            String spanKind = span.getSpanKind();
            Set<TelemetryAttribute> attributes =
                kind.computeIfAbsent(spanKind, k -> new HashSet<>());

            if (span.getAttributes() != null) {
              for (TelemetryAttribute attr : span.getAttributes()) {
                attributes.add(new TelemetryAttribute(attr.getName(), attr.getType()));
              }
            }
          }
        }
      }
    }

    Map<String, List<EmittedSpans.Span>> newSpans = new HashMap<>();
    for (Map.Entry<String, Map<String, Set<TelemetryAttribute>>> entry : raw.entrySet()) {
      String when = entry.getKey();
      Map<String, Set<TelemetryAttribute>> attributesByKind = entry.getValue();

      for (Map.Entry<String, Set<TelemetryAttribute>> kindEntry : attributesByKind.entrySet()) {
        String spanKind = kindEntry.getKey();
        Set<TelemetryAttribute> attributes = kindEntry.getValue();

        List<EmittedSpans.Span> spans = newSpans.computeIfAbsent(when, k -> new ArrayList<>());
        spans.add(new EmittedSpans.Span(spanKind, new ArrayList<>(attributes)));
      }
    }

    return newSpans;
  }

  void analyzeVersions(List<String> files, InstrumentationModule module) {
    Map<InstrumentationType, Set<String>> versions = new HashMap<>();
    for (String file : files) {
      String fileContents = FileManager.readFileToString(file);
      if (fileContents == null) {
        continue;
      }

      DependencyInfo results = null;

      if (file.contains("/javaagent/")) {
        results = parseGradleFile(fileContents, InstrumentationType.JAVAAGENT);
        versions
            .computeIfAbsent(InstrumentationType.JAVAAGENT, k -> new HashSet<>())
            .addAll(results.versions());
      } else if (file.contains("/library/")) {
        results = parseGradleFile(fileContents, InstrumentationType.LIBRARY);
        versions
            .computeIfAbsent(InstrumentationType.LIBRARY, k -> new HashSet<>())
            .addAll(results.versions());
      }
      if (results != null && results.minJavaVersionSupported() != null) {
        module.setMinJavaVersion(results.minJavaVersionSupported());
      }
    }
    module.setTargetVersions(versions);
  }
}
