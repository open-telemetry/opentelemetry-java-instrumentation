/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter.internal;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizerUtil;
import io.opentelemetry.instrumentation.api.internal.ServiceLoaderUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumenterCustomizerUtil {

  static {
    List<InternalInstrumenterCustomizerProvider> providers = new ArrayList<>();
    for (InstrumenterCustomizerProvider provider :
        ServiceLoaderUtil.load(
            InstrumenterCustomizerProvider.class, InstrumenterCustomizerUtil::loadProviders)) {
      providers.add(new InternalInstrumenterCustomizerProviderImpl(provider));
    }
    InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(providers);
  }

  private static List<InstrumenterCustomizerProvider> loadProviders() {
    List<InstrumenterCustomizerProvider> providers = new ArrayList<>();
    // Android's R8 optimizer can replace discovery with direct instantiation when the service class
    // literal, its defining class loader, and iteration are in the same method.
    for (InstrumenterCustomizerProvider provider :
        ServiceLoader.load(
            InstrumenterCustomizerProvider.class,
            InstrumenterCustomizerProvider.class.getClassLoader())) {
      providers.add(provider);
    }
    return providers;
  }

  private InstrumenterCustomizerUtil() {}
}
