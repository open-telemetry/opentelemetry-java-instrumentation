/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface InternalInstrumenterCustomizerProvider {

  void customize(InternalInstrumenterCustomizer<?, ?> customizer);
}
