/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

final class ProcessingInvocation<REQUEST> {

  final REQUEST request;
  @Nullable final Context context;
  @Nullable final Scope scope;
  @Nullable final ProcessingInvocation<REQUEST> previous;
  @Nullable Throwable error;
  boolean completed;

  ProcessingInvocation(
      REQUEST request,
      @Nullable Context context,
      @Nullable ProcessingInvocation<REQUEST> previous) {
    this.request = request;
    this.context = context;
    this.previous = previous;
    this.scope = context == null ? null : context.makeCurrent();
  }
}
