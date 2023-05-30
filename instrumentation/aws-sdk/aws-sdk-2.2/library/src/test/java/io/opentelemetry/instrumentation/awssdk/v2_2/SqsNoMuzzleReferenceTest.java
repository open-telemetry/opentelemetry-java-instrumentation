/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * This unit test should be a muzzle directive. It verifies that certain references are *not* present
 * (or sources annotated with {@code @NoMuzzle}).
 */
public class SqsNoMuzzleReferenceTest {
  @Test
  public void shouldNotReferenceSqs() {
    ServiceLoader<InstrumentationModule> loader = ServiceLoader.load(InstrumentationModule.class);
    assertThat(loader)
        .as("Contains the main instrumentation module we want to test")
        .anyMatch(
            m ->
                m.getClass()
                    .getName()
                    .equals(
                        "io.opentelemetry.javaagent.instrumentation.awssdk.v2_2.AwsSdkInstrumentationModule"));
    assertThat(loader)
        .allSatisfy(
            module -> {
              Collection<ClassRef> refs = InstrumentationModuleMuzzle.getMuzzleReferences(
                  module).values();
              // TODO: Ideally, we would not like to filter by class name but by defining artifact (jar(s))
              List<ClassRef> badRefs = refs.stream()
                  .filter(ref -> ref.getClassName().startsWith("software.amazon.")
                      && ref.getClassName().toLowerCase(Locale.ROOT).contains("sqs")).collect(
                      Collectors.toList());
              assertThat(badRefs)
                  .as("Unexpected references, use printMuzzleReferences Gradle task to find sources")
                  .isEmpty();
            });

  }
}
