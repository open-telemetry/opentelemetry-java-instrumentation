/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UtilsTest {

  @Test
  void getResourceName_addsSuffixAndConvertsDots() {
    String result = Utils.getResourceName("com.example.Something");
    assertEquals("com/example/Something.class", result);
  }

  @Test
  void getClassName_convertsSlashesToDots() {
    String result = Utils.getClassName("com/example/Something");
    assertEquals("com.example.Something", result);
  }

  @Test
  void getInternalName_convertsDotsToSlashes() {
    String result = Utils.getInternalName(UtilsTest.class);
    assertEquals("io/opentelemetry/javaagent/tooling/UtilsTest", result);
  }
}