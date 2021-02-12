/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import io.opentelemetry.instrumentation.testing.LibraryTestRunner;

/**
 * JUnit 5 extension for writing library instrumentation tests.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MyLibraryInstrumentationTest {
 *     {@literal @}RegisterExtension
 *     static final LibraryInstrumentationExtension instrTesting = LibraryInstrumentationExtension.create();
 *
 *     {@literal @}Test
 *     void test() {
 *       // test code ...
 *
 *       var spans = instrTesting.spans();
 *       // assertions on collected spans ...
 *     }
 *   }
 * </pre>
 */
public final class LibraryInstrumentationExtension extends InstrumentationExtension {
  private LibraryInstrumentationExtension() {
    super(LibraryTestRunner.instance());
  }

  public static LibraryInstrumentationExtension create() {
    return new LibraryInstrumentationExtension();
  }
}
