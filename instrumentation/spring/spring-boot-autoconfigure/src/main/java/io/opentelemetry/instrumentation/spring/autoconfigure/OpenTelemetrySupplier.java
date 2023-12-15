/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import java.util.function.Supplier;

/** To inject an OpenTelemetry into bean post processors */
public interface OpenTelemetrySupplier extends Supplier<OpenTelemetry> {}
