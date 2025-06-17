/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter.internal;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizerProvider;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalInstrumenterCustomizerProviderImpl
    implements InternalInstrumenterCustomizerProvider {
  private final InstrumenterCustomizerProvider provider;

  public InternalInstrumenterCustomizerProviderImpl(InstrumenterCustomizerProvider provider) {
    this.provider = provider;
  }

  @Override
  public void customize(InternalInstrumenterCustomizer<?, ?> customizer) {
    provider.customize(new InstrumenterCustomizerImpl(customizer));
  }
}
