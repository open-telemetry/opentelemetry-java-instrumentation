/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassLoaderMatcherTest {

  private final InstrumentationModule core = new CoreModule();
  private final InstrumentationModule companion = new CompanionModule();

  @Test
  void modulesCanShareAllPublicNames() {
    assertThat(ClassLoaderMatcher.selectModules(asList(core, companion), emptySet(), emptySet()))
        .containsExactly(core, companion);
  }

  @Test
  void excludesOneClassWithoutExcludingItsSharedNames() {
    assertThat(
            ClassLoaderMatcher.selectModules(
                asList(core, companion), emptySet(), singleton(companion.getClass().getName())))
        .containsExactly(core);
  }

  @Test
  void preservesNameBasedExclusions() {
    assertThat(
            ClassLoaderMatcher.selectModules(
                asList(core, companion), singleton("example"), emptySet()))
        .isEmpty();
  }

  @Test
  void combinesClassAndNameExclusions() {
    assertThat(
            ClassLoaderMatcher.selectModules(
                asList(core, companion),
                singleton("example"),
                singleton(companion.getClass().getName())))
        .isEmpty();
  }

  @Test
  void rejectsUnknownClassEvenWhenEveryModuleIsExcludedByName() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ClassLoaderMatcher.selectModules(
                    asList(core, companion), singleton("example"), singleton("missing.Module")))
        .withMessage("Excluded InstrumentationModule classes were not found: [missing.Module]");
  }

  @Test
  void rejectsSimpleClassNames() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ClassLoaderMatcher.selectModules(
                    asList(core, companion), emptySet(), singleton("CompanionModule")))
        .withMessage("Excluded InstrumentationModule classes were not found: [CompanionModule]");
  }

  private static class CoreModule extends InstrumentationModule {
    CoreModule() {
      super("example", "example-1.0");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
      return emptyList();
    }
  }

  private static class CompanionModule extends InstrumentationModule {
    CompanionModule() {
      super("example", "example-1.0");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
      return emptyList();
    }
  }
}
