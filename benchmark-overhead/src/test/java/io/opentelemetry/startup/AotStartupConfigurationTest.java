/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AotStartupConfigurationTest {
  @Test
  void aotIsTheOnlyDifferenceWithinEachPair() {
    for (Variant variant : List.of(Variant.AOT_NO_AGENT, Variant.AOT_AGENT)) {
      List<String> arguments = new ArrayList<>(AotStartupBenchmark.arguments(variant, false));
      assertThat(arguments).contains("-XX:AOTMode=on");
      arguments.removeIf(argument -> argument.startsWith("-XX:AOT"));
      assertThat(arguments)
          .isEqualTo(
              AotStartupBenchmark.arguments(
                  variant.agent() ? Variant.NORMAL_AGENT : Variant.NORMAL_NO_AGENT, false));
    }
  }

  @Test
  void noAgentPairHasNoAgentOrBootstrapPreload() {
    for (Variant variant : List.of(Variant.NORMAL_NO_AGENT, Variant.AOT_NO_AGENT)) {
      assertThat(AotStartupBenchmark.arguments(variant, false))
          .noneMatch(argument -> argument.contains("agent.jar"))
          .doesNotContain("--add-modules=java.instrument");
    }
  }

  @Test
  void timedRunsLeaveFieldInjectionEnabledAndOmitDiagnostics() {
    for (Variant variant : Variant.values()) {
      assertThat(AotStartupBenchmark.arguments(variant, false))
          .noneMatch(argument -> argument.contains("field-injection"))
          .noneMatch(argument -> argument.startsWith("-Xlog:"))
          .noneMatch(argument -> argument.contains("traceUsage"))
          .doesNotContain("-Dotel.javaagent.debug=true", "-XX:+VerifySharedSpaces", "-Xshare:off");
    }
  }
}
