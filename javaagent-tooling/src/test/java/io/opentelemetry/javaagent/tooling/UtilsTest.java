/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtilsTest {

  @Test
  void getResourceName_addsSuffixAndConvertsDots() {
    String result = Utils.getResourceName("com.example.Something");
    assertThat(result).isEqualTo("com/example/Something.class");
  }

  @Test
  void getClassName_convertsSlashesToDots() {
    String result = Utils.getClassName("com/example/Something");
    assertThat(result).isEqualTo("com.example.Something");
  }

  @Test
  void getInternalName_convertsDotsToSlashes() {
    String result = Utils.getInternalName(UtilsTest.class);
    assertThat(result).isEqualTo("io/opentelemetry/javaagent/tooling/UtilsTest");
  }
}