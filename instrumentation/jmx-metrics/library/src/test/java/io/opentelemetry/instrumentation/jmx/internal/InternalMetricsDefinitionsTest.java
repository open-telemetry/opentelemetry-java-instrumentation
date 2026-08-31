/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal;

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

class InternalMetricsDefinitionsTest {
  private static final ClassLoader CLASS_LOADER =
      InternalMetricsDefinitionsTest.class.getClassLoader();
  private static final Path YAML_ROOT_FOLDER =
      Paths.get("src", "main", "resources", "jmx", "rules");

  @Test
  void supportedSystems() throws IOException {
    assertThat(InternalMetricsDefinitions.getSupportedSystems())
        .containsExactlyInAnyOrder(
            "activemq",
            "camel",
            "cassandra",
            "kafka-broker",
            "kafka-connect",
            "hadoop",
            "jetty",
            "jvm",
            "tomcat",
            "wildfly");

    InternalMetricsDefinitions definitions = new InternalMetricsDefinitions(CLASS_LOADER);

    Set<String> allRules = new HashSet<>();
    for (String system : InternalMetricsDefinitions.getSupportedSystems()) {
      Set<String> rulesForSystem = definitions.getRulesForSystem(system, true, true);
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

  @Test
  void loadByStability() {
    // we intentionally use a fake (unsupported) system to test resource resolution
    InternalMetricsDefinitions definitions = new InternalMetricsDefinitions(CLASS_LOADER);
    assertThat(definitions.getRulesForSystem("fake-system", false, false)).isEmpty();
    assertThat(definitions.getRulesForSystem("fake-system", true, false))
        .containsExactlyInAnyOrder("jmx/rules/fake-system.yaml");
    assertThat(definitions.getRulesForSystem("fake-system", false, true))
        .containsExactlyInAnyOrder("jmx/rules/fake-system_unstable.yaml");
    assertThat(definitions.getRulesForSystem("fake-system", true, true))
        .containsExactlyInAnyOrder(
            "jmx/rules/fake-system.yaml", "jmx/rules/fake-system_unstable.yaml");
  }

  @Test
  void loadMissing() {
    InternalMetricsDefinitions definitions = new InternalMetricsDefinitions(CLASS_LOADER);
    assertThat(definitions.getRulesForSystem("missing-system", true, true)).isEmpty();
  }
}
