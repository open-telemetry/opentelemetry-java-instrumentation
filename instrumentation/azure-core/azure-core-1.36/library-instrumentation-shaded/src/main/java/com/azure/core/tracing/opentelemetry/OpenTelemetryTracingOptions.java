/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.azure.core.tracing.opentelemetry;

import com.azure.core.util.TracingOptions;

/**
 * Replace {@link OpenTelemetryTracingOptions} from com.azure:azure-core-tracing-opentelemetry with
 * a stub. Auto instrumentation does not use {@link OpenTelemetryTracingOptions}. This is needed
 * because {@link OpenTelemetryTracingOptions} calls super constructor in {@link TracingOptions}
 * that does exist in com.azure:azure-core:1.36.0 which triggers muzzle failure.
 */
public class OpenTelemetryTracingOptions extends TracingOptions {}
