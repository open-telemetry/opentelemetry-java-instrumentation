/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;

/**
 * Marker interface for instrumentation modules whose virtual fields should be set up before
 * OpenTelemetry SDK is initialized.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface EarlyInstrumentationModule extends Ordered {

  default InstrumentationModule getInstrumentationModule() {
    return (InstrumentationModule) this;
  }
}
