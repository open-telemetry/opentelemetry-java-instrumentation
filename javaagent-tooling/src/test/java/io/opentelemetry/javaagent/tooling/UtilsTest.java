/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtilsTest {

  @Test
  void getResourceNameAddsSuffixAndConvertDotsToSlashes() {
    String result = Utils.getResourceName("com.example.Something");

    assertThat(result).isEqualTo("com/example/Something.class");
  }
}
