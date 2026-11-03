/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class MuzzleDirectiveTest {

  @Test
  void exclusionsDefaultToEmpty() {
    MuzzleDirective directive =
        ProjectBuilder.builder().build().getObjects().newInstance(MuzzleDirective.class);

    assertThat(directive.getExcludedInstrumentationNames().get()).isEmpty();
    assertThat(directive.getExcludedInstrumentationModules().get()).isEmpty();
  }

  @Test
  void classExclusionsAreIndependentOfPublicNames() {
    MuzzleDirective directive =
        ProjectBuilder.builder().build().getObjects().newInstance(MuzzleDirective.class);

    directive.excludeInstrumentationModule("example.CoreModule");
    directive.excludeInstrumentationModule("example.CoreModule");
    directive.excludeInstrumentationName("example");

    assertThat(directive.getExcludedInstrumentationModules().get())
        .containsExactly("example.CoreModule");
    assertThat(directive.getExcludedInstrumentationNames().get()).containsExactly("example");
  }
}
