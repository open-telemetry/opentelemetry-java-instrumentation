/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.instrumentation.annotations.Counted;

class CountedMethods {

  @Counted
  void defaultName() {}

  @Counted("custom.count")
  void customName() {}

  @Counted("exception.count")
  void throwsException() {
    throw new IllegalStateException("boom");
  }
}
