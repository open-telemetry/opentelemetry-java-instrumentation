/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.awssdk.v2_2.AwsSdkInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.awssdk.v2_2.SqsInstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * This unit test should be a muzzle directive. It verifies that certain references are *not*
 * present (or sources annotated with {@code @NoMuzzle}).
 */
public class SqsNoMuzzleReferenceTest {
  @Test
  public void shouldNotReferenceSqsFromMainModule() {
    List<ClassRef> badRefs = getSqsRefs(new AwsSdkInstrumentationModule());
    assertThat(badRefs)
        .as("Unexpected references, use printMuzzleReferences Gradle task to find sources")
        .isEmpty();
  }

  @Test
  public void shouldReferenceSqsFromSqsModule() {
    List<ClassRef> expectedRefs = getSqsRefs(new SqsInstrumentationModule());
    assertThat(expectedRefs)
        .as("References from Sqs module should not be empty, maybe bad use of @NoMuzzle")
        .isNotEmpty();
  }

  private static List<ClassRef> getSqsRefs(InstrumentationModule module) {
    Collection<ClassRef> refs = InstrumentationModuleMuzzle.getMuzzleReferences(module).values();
    return refs.stream()
        .filter(
            ref ->
                ref.getClassName().startsWith("software.amazon.")
                    && ref.getClassName().toLowerCase(Locale.ROOT).contains("sqs"))
        .collect(Collectors.toList());
  }
}
