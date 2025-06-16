/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

/**
 * A service provider interface (SPI) for customizing instrumentation behavior.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface InstrumenterCustomizerProvider {

  /**
   * Returns a predicate that matches the instrumentation name for which this provider is
   * applicable.
   *
   * @return a predicate that matches the instrumentation name for which this provider is applicable
   */
  String getInstrumentationName();

  /**
   * Customizes the given instrumenter.
   *
   * <p>This method is called for each instrumenter being built. Implementations can use the
   * provided customizer to add or modify behavior of the instrumenter.
   *
   * @param customizer the customizer for the instrumenter being built
   */
  void customize(InstrumenterCustomizer customizer);
}
