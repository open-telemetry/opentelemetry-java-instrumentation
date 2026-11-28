/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassAndMethodTest {

  @Test
  void codeAttributesGetterIsShared() {
    assertThat(ClassAndMethod.codeAttributesGetter())
        .isSameAs(ClassAndMethod.codeAttributesGetter());
  }
}
