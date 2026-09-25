/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class ShadingTest {

  private static final List<String> EXPECTED_ENTRY_PREFIXES =
      asList("io/opentelemetry/javaagent/", "inst/", "META-INF/");

  @Test
  void agentJarContainsOnlyExpectedEntries() throws Exception {
    String agentJarPath = getAgentJarPath();
    assertThat(agentJarPath).isNotNull();

    File agentJar = new File(agentJarPath);
    assertThat(agentJar).exists();
    assertThat(agentJar).isFile();

    List<String> unexpectedEntries = new ArrayList<>();

    try (JarFile jarFile = new JarFile(agentJar)) {
      jarFile.stream()
          .map(JarEntry::getName)
          .filter(entryName -> !entryName.endsWith("/")) // Skip directories
          .forEach(
              entryName -> {
                boolean isExpected =
                    EXPECTED_ENTRY_PREFIXES.stream().anyMatch(entryName::startsWith);
                if (!isExpected) {
                  unexpectedEntries.add(entryName);
                }
              });
    }

    assertThat(unexpectedEntries).isEmpty();
  }

  @Test
  void agentJarContainsComposableRuleBasedSamplerOnly() throws Exception {
    String agentJarPath = getAgentJarPath();
    assertThat(agentJarPath).isNotNull();

    try (JarFile jarFile = new JarFile(agentJarPath)) {
      assertThat(
              jarFile.getJarEntry(
                  "inst/io/opentelemetry/sdk/extension/incubator/trace/samplers/"
                      + "ComposableRuleBasedSamplerBuilder.classdata"))
          .isNotNull();
      assertThat(
              jarFile.getJarEntry(
                  "inst/io/opentelemetry/contrib/sampler/RuleBasedRoutingSampler.classdata"))
          .isNull();
    }
  }

  private static String getAgentJarPath() {
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    for (String arg : runtimeMxBean.getInputArguments()) {
      if (arg.startsWith("-javaagent:")) {
        return arg.substring("-javaagent:".length());
      }
    }
    return null;
  }
}
