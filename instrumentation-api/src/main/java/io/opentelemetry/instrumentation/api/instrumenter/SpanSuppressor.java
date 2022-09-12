/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

interface SpanSuppressor {

  Context storeInContext(Context context, SpanKind spanKind, Span span);

  boolean shouldSuppress(Context parentContext, SpanKind spanKind);
}
