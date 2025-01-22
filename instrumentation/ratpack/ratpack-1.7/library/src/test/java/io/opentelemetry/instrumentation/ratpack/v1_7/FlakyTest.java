/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class FlakyTest {

  @Test
  void flakyTest() {
    assertThat(new Random().nextInt(10)).isLessThan(3);
  }
}
