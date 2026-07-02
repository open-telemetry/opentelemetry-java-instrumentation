/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class SchedulerErrorAttributesExtractor implements AttributesExtractor<SchedulerError, Void> {

  // Experimental attribute: name/shape may change until scheduler instrumentation stabilizes.
  private static final AttributeKey<String> SCHEDULER_NAME =
      AttributeKey.stringKey("quartz.scheduler.name");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, SchedulerError request) {
    attributes.put(SCHEDULER_NAME, request.getSchedulerName());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SchedulerError request,
      @Nullable Void response,
      @Nullable Throwable error) {}
}
