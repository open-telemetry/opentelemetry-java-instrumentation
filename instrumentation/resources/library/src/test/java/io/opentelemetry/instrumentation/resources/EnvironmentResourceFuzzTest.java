/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonMap;

import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import edu.berkeley.cs.jqf.fuzz.junit.GuidedFuzzing;
import edu.berkeley.cs.jqf.fuzz.random.NoGuidance;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.PercentEscaper;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

@SuppressWarnings("SystemOut")
class EnvironmentResourceFuzzTest {

  @RunWith(JQF.class)
  public static class TestCases {

    private static final PercentEscaper escaper = PercentEscaper.create();

    @Fuzz
    public void getAttributesWithRandomValues(String value1, String value2) {
      Attributes attributes =
          EnvironmentResource.getAttributes(
              DefaultConfigProperties.createForTest(
                  singletonMap(
                      EnvironmentResource.ATTRIBUTE_PROPERTY,
                      "key1=" + escaper.escape(value1) + ",key2=" + escaper.escape(value2))));

      assertThat(attributes).hasSize(2).containsEntry("key1", value1).containsEntry("key2", value2);
    }
  }

  // driver methods to avoid having to use the vintage junit engine, and to enable increasing the
  // number of iterations:

  @Test
  void getAttributesWithFuzzing() {
    Result result =
        GuidedFuzzing.run(
            TestCases.class,
            "getAttributesWithRandomValues",
            new NoGuidance(10000, System.out),
            System.out);
    Assertions.assertThat(result.wasSuccessful()).isTrue();
  }
}
