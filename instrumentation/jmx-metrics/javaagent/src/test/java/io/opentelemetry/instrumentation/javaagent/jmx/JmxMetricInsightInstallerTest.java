/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.jmx;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
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
      new HashSet<>(Arrays.asList("activemq.yaml", "camel.yaml", "kafka-broker.yaml"));

  @Test
  void testToVerifyExistingRulesAreValid() throws Exception {
    RuleParser parser = RuleParser.get();
    assertThat(parser).isNotNull();

    Path path = Paths.get(PATH_TO_ALL_EXISTING_RULES);
    assertThat(path).isNotEmptyDirectory();

    for (String file : FILES_TO_BE_TESTED) {
      Path filePath = path.resolve(file);
      assertThat(filePath).isRegularFile();

      // loading rules from direct file access
      JmxTelemetry.builder(OpenTelemetry.noop()).addCustomRules(filePath);
    }
  }
}
