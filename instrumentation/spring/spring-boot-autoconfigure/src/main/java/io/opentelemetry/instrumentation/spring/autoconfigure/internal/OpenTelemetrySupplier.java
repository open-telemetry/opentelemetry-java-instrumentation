/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.api.OpenTelemetry;
import java.util.function.Supplier;

/**
 * Used to provide access to the OpenTelemetry instance in bean post processors.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface OpenTelemetrySupplier extends Supplier<OpenTelemetry> {}
