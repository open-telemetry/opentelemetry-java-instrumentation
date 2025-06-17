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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumenterCustomizerUtil {

  static {
    List<InternalInstrumenterCustomizerProvider> providers = new ArrayList<>();
    for (InstrumenterCustomizerProvider provider :
        ServiceLoaderUtil.load(InstrumenterCustomizerProvider.class)) {
      providers.add(new InternalInstrumenterCustomizerProviderImpl(provider));
    }
    InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(providers);
  }

  private InstrumenterCustomizerUtil() {}
}
