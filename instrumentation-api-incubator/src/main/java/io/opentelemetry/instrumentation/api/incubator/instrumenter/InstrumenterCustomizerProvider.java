/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter;

/**
 * A service provider interface (SPI) for customizing instrumentation behavior.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface InstrumenterCustomizerProvider {

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
