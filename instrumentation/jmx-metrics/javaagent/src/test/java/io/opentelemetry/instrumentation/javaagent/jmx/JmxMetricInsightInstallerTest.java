/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.jmx;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.jmx.yaml.JmxConfig;
import io.opentelemetry.instrumentation.jmx.yaml.JmxRule;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JmxMetricInsightInstallerTest {
  private static final String PATH_TO_ALL_EXISTING_RULES = "src/main/resources/jmx/rules";
  private static RuleParser parser;
  private static final Set<String> FILES_TO_BE_TESTED =
      new HashSet<>(
          Arrays.asList(
              "activemq.yaml",
              "hadoop.yaml",
              "jetty.yaml",
              "kafka-broker.yaml",
              "tomcat.yaml",
              "wildfly.yaml"));

  @BeforeAll
  static void setup() {
    parser = RuleParser.get();
    assertThat(parser == null).isFalse();
  }

  @Test
  void testToVerifyExistingRulesAreValid() throws Exception {
    File existingRulesDir = new File(PATH_TO_ALL_EXISTING_RULES);
    assertThat(existingRulesDir == null).isFalse();

    // make sure we have correct number of files
    File[] existingRules = existingRulesDir.listFiles();
    assertThat(existingRules).hasSize(FILES_TO_BE_TESTED.size());

    for (File file : existingRules) {
      // also make sure the files name are matching
      if (FILES_TO_BE_TESTED.contains(file.getName())) {
        testRulesAreValid(file);
      }
    }
  }

  void testRulesAreValid(File file) throws Exception {
    InputStream inputStream = new FileInputStream(file);
    JmxConfig config = parser.loadConfig(inputStream);
    assertThat(config).isNotNull();

    List<JmxRule> defs = config.getRules();
    // make sure all the rules in that file are valid
    for (JmxRule rule : defs) {
      rule.buildMetricDef();
    }
  }
}
