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

  @SuppressWarnings("UnicodeInCode")
  @Counted
  void ünicödeMethödNamë() {}

  static class GenericMethods<T> {
    T customGeneric(T result) {
      return result;
    }
  }

  static class StringCountedMethods extends GenericMethods<String> {

    @Override
    @Counted("custom.generic")
    String customGeneric(String result) {
      return result;
    }
  }
}
