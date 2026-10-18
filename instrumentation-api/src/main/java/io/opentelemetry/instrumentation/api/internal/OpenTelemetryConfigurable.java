/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Initializes a component with the {@link OpenTelemetry} instance that owns its instrumenter.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface OpenTelemetryConfigurable {

  void internalInitialize(OpenTelemetry openTelemetry);
}
