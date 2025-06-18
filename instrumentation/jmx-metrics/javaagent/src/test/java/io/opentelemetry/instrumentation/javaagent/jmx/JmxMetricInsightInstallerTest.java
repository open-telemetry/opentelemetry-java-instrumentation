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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * TODO: This test will eventually go away when all yaml files are moved from javaagent to library
 * directory. When yaml file is moved from javaagent to library then appropriate item must be
 * removed from JmxMetricInsightInstallerTest#FILES_TO_BE_TESTED and corresponding test must be
 * added in the library.
 */
class JmxMetricInsightInstallerTest {
  private static final String PATH_TO_ALL_EXISTING_RULES = "src/main/resources/jmx/rules";
  private static final Set<String> FILES_TO_BE_TESTED =
      new HashSet<>(
          Arrays.asList(
              "activemq.yaml", "camel.yaml", "hadoop.yaml", "kafka-broker.yaml", "wildfly.yaml"));

  @Test
  void testToVerifyExistingRulesAreValid() throws Exception {
    RuleParser parser = RuleParser.get();
    assertThat(parser).isNotNull();

    Path path = Paths.get(PATH_TO_ALL_EXISTING_RULES);
    assertThat(Files.exists(path)).isTrue();

    File existingRulesDir = path.toFile();
    File[] existingRules = existingRulesDir.listFiles();
    Set<String> filesChecked = new HashSet<>();

    for (File file : existingRules) {
      // make sure we only test the files that we supposed to test
      String fileName = file.getName();
      if (FILES_TO_BE_TESTED.contains(fileName)) {
        testRulesAreValid(file, parser);
        filesChecked.add(fileName);
      }
    }
    // make sure we checked all the files that are supposed to be here
    assertThat(filesChecked).isEqualTo(FILES_TO_BE_TESTED);
  }

  void testRulesAreValid(File file, RuleParser parser) throws Exception {
    try (InputStream inputStream = new FileInputStream(file)) {
      JmxConfig config = parser.loadConfig(inputStream);
      assertThat(config).isNotNull();

      List<JmxRule> defs = config.getRules();
      // make sure all the rules in that file are valid
      for (JmxRule rule : defs) {
        rule.buildMetricDef();
      }
    }
  }
}
