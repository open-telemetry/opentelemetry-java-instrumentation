/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal;

import static io.opentelemetry.instrumentation.jmx.internal.JmxTelemetryRules.locateRulesForSystem;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class JmxTelemetryRulesTest {

  private static final Path YAML_ROOT_FOLDER =
      Paths.get("src", "main", "resources", "jmx", "rules");

  private static final ClassLoader classLoader = JmxTelemetryRulesTest.class.getClassLoader();

  @Test
  void locateStableUnstable() {
    assertThat(locateRulesForSystem(classLoader, "fake-rules", false))
        .containsExactly("jmx/rules/fake-rules.yaml");

    assertThat(locateRulesForSystem(classLoader, "fake-rules", true))
        .containsExactlyInAnyOrder(
            "jmx/rules/fake-rules.yaml", "jmx/rules/fake-rules_unstable.yaml");
  }

  @Test
  void supportedSystems() throws IOException {
    assertThat(JmxTelemetryRules.getSupportedSystems())
        .containsExactlyInAnyOrder(
            "activemq",
            "camel",
            "experimental-cassandra",
            "experimental-kafka-connect",
            "hadoop",
            "jetty",
            "jvm",
            "tomcat",
            "wildfly");

    Set<String> allRules = new HashSet<>();
    for (String system : JmxTelemetryRules.getSupportedSystems()) {
      Set<String> rulesForSystem = locateRulesForSystem(classLoader, system, true);
      assertThat(rulesForSystem).isNotEmpty();
      allRules.addAll(rulesForSystem);
    }

    assertThat(allRules).containsExactlyInAnyOrderElementsOf(getYamlFilesFromFileSystem());
  }

  private static Set<String> getYamlFilesFromFileSystem() throws IOException {
    Path rulesRoot = YAML_ROOT_FOLDER;
    try (Stream<Path> stream = Files.walk(rulesRoot)) {
      return stream
          .filter(Files::isRegularFile)
          .map(path -> "jmx/rules/" + rulesRoot.relativize(path))
          .collect(toSet());
    }
  }
}
