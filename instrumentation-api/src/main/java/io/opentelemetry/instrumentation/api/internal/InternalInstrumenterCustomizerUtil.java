/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.Collections;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InternalInstrumenterCustomizerUtil {
  static {
    instrumenterCustomizerProviders = Collections.emptyList();
    try {
      // initializing InstrumenterCustomizerUtil will call setInstrumenterCustomizerProviders on
      // this class
      Class.forName(
          "io.opentelemetry.instrumentation.api.incubator.instrumenter.internal.InstrumenterCustomizerUtil");
    } catch (ClassNotFoundException exception) {
      // incubator api not available, ignore
    }
  }

  private static volatile List<InternalInstrumenterCustomizerProvider>
      instrumenterCustomizerProviders;

  public static void setInstrumenterCustomizerProviders(
      List<InternalInstrumenterCustomizerProvider> providers) {
    instrumenterCustomizerProviders = providers;
  }

  public static List<InternalInstrumenterCustomizerProvider> getInstrumenterCustomizerProviders() {
    return instrumenterCustomizerProviders;
  }

  private InternalInstrumenterCustomizerUtil() {}
}
