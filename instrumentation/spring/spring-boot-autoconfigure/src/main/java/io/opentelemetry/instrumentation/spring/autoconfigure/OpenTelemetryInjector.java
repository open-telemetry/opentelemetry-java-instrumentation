/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import java.util.function.Consumer;

/** To inject an OpenTelemetry bean into non-Spring components */
public interface OpenTelemetryInjector extends Consumer<OpenTelemetry> {}
