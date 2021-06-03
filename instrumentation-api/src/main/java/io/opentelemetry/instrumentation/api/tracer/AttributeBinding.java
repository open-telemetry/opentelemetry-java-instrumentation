/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.SpanBuilder;

@FunctionalInterface
public interface AttributeBinding {
  SpanBuilder apply(SpanBuilder builder, Object arg);
}
