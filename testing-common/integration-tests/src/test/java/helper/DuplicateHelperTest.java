/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DuplicateHelperTest {

  @Test
  void duplicateHelper() {
    String string = DuplicateHelperTestClass.transform("test");
    assertThat(string).isEqualTo("test foo");
  }
}
