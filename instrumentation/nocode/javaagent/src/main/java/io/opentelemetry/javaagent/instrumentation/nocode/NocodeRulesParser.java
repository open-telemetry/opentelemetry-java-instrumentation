/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.javaagent.bootstrap.NocodeInstrumentationRules;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public final class NocodeRulesParser {
  private static final Logger logger = Logger.getLogger(NocodeRulesParser.class.getName());
  // FIXME switch name
  private static final String NOCODE_YMLFILE = "splunk.otel.instrumentation.nocode.yml.file";

  private final List<NocodeInstrumentationRules.Rule> instrumentationRules;

  public NocodeRulesParser(ConfigProperties config) {
    instrumentationRules = Collections.unmodifiableList(new ArrayList<>(load(config)));
  }

  public List<NocodeInstrumentationRules.Rule> getInstrumentationRules() {
    return instrumentationRules;
  }

  private static List<NocodeInstrumentationRules.Rule> load(ConfigProperties config) {
    String yamlFileName = config.getString(NOCODE_YMLFILE);
    if (yamlFileName == null || yamlFileName.trim().isEmpty()) {
      return Collections.emptyList();
    }

    try {
      return loadUnsafe(yamlFileName);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Can't load configured nocode yaml.", e);
      return Collections.emptyList();
    }
  }

  // FIXME rework with builder
  @SuppressWarnings("unchecked")
  private static List<NocodeInstrumentationRules.Rule> loadUnsafe(String yamlFileName)
      throws Exception {
    List<NocodeInstrumentationRules.Rule> answer = new ArrayList<>();
    try (InputStream inputStream = Files.newInputStream(Paths.get(yamlFileName.trim()))) {
      Load load = new Load(LoadSettings.builder().build());
      Iterable<Object> parsedYaml = load.loadAllFromInputStream(inputStream);
      for (Object yamlBit : parsedYaml) {
        List<Map<String, Object>> rulesMap = (List<Map<String, Object>>) yamlBit;
        for (Map<String, Object> yamlRule : rulesMap) {
          // FUTURE support more complex class selection (inherits-from, etc.)
          String className = yamlRule.get("class").toString();
          // FUTURE support method override selection - e.g., with classfile method signature or
          // something
          String methodName = yamlRule.get("method").toString();
          String spanName =
              yamlRule.get("spanName") == null ? null : yamlRule.get("spanName").toString();
          String spanKind =
              yamlRule.get("spanKind") == null ? null : yamlRule.get("spanKind").toString();
          String spanStatus =
              yamlRule.get("spanStatus") == null ? null : yamlRule.get("spanStatus").toString();

          Map<String, String> ruleAttributes = new HashMap<>();
          List<Map<String, Object>> attrs = (List<Map<String, Object>>) yamlRule.get("attributes");
          if (attrs != null) {
            for (Map<String, Object> attr : attrs) {
              ruleAttributes.put(attr.get("key").toString(), attr.get("value").toString());
            }
          }
          answer.add(
              new NocodeInstrumentationRules.Rule(
                  className, methodName, spanName, spanKind, spanStatus, ruleAttributes));
        }
      }
    }

    return answer;
  }
}
