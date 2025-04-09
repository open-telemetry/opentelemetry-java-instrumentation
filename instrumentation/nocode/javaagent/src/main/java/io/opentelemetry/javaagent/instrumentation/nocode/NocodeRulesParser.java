/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeExpression;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeInstrumentationRules;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.jexl3.JexlExpression;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

class NocodeRulesParser {
  private static final String NOCODE_YMLFILE = "otel.java.instrumentation.nocode.yml.file";

  private static final Logger logger = Logger.getLogger(NocodeRulesParser.class.getName());

  private final List<NocodeInstrumentationRules.Rule> instrumentationRules;
  private JexlEvaluator evaluator;

  public NocodeRulesParser(ConfigProperties config) {
    instrumentationRules = Collections.unmodifiableList(new ArrayList<>(load(config)));
  }

  public List<NocodeInstrumentationRules.Rule> getInstrumentationRules() {
    return instrumentationRules;
  }

  private List<NocodeInstrumentationRules.Rule> load(ConfigProperties config) {
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

  @SuppressWarnings("unchecked")
  private List<NocodeInstrumentationRules.Rule> loadUnsafe(String yamlFileName) throws Exception {
    List<NocodeInstrumentationRules.Rule> answer = new ArrayList<>();
    try (InputStream inputStream = Files.newInputStream(Paths.get(yamlFileName.trim()))) {
      Load load = new Load(LoadSettings.builder().build());
      Iterable<Object> parsedYaml = load.loadAllFromInputStream(inputStream);
      for (Object yamlBit : parsedYaml) {
        List<Map<String, Object>> rulesMap = (List<Map<String, Object>>) yamlBit;
        for (Map<String, Object> yamlRule : rulesMap) {
          NocodeInstrumentationRules.Builder builder = new NocodeInstrumentationRules.Builder();

          // FUTURE support more complex class selection (inherits-from, wildcards, etc.)
          builder.className(yamlRule.get("class").toString());
          // FUTURE support more complex method (specific overrides, wildcards, etc.)
          builder.methodName(yamlRule.get("method").toString());
          builder.spanName(toExpression(yamlRule.get("spanName")));
          if (yamlRule.get("spanKind") != null) {
            String spanKind = yamlRule.get("spanKind").toString();
            try {
              builder.spanKind(SpanKind.valueOf(spanKind.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
              logger.warning("Invalid span kind " + spanKind);
            }
          }
          builder.spanStatus(toExpression(yamlRule.get("spanStatus")));

          List<Map<String, Object>> attrs = (List<Map<String, Object>>) yamlRule.get("attributes");
          if (attrs != null) {
            for (Map<String, Object> attr : attrs) {
              builder.attribute(attr.get("key").toString(), toExpression(attr.get("value")));
            }
          }
          answer.add(builder.build());
        }
      }
    }

    return answer;
  }

  private NocodeExpression toExpression(Object ruleNode) {
    if (ruleNode == null) {
      return null;
    }

    String expressionText = ruleNode.toString();
    if (expressionText == null) {
      return null;
    }

    if (evaluator == null) {
      evaluator = new JexlEvaluator();
    }

    JexlExpression jexlExpression = evaluator.createExpression(expressionText);
    if (jexlExpression == null) {
      return null;
    }

    return new NocodeExpression() {
      @Override
      public Object evaluate(Object thiz, Object[] params) {
        return evaluator.evaluate(jexlExpression, thiz, params);
      }

      @Override
      public Object evaluateAtEnd(
          Object thiz, Object[] params, Object returnValue, Throwable error) {
        return evaluator.evaluateAtEnd(jexlExpression, thiz, params, returnValue, error);
      }

      @Override
      public String toString() {
        return expressionText;
      }
    };
  }
}
