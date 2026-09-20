/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * Tracks the SQS event selected for processing in one Lambda invocation. A later invocation starts
 * from a new context, even if application code reuses the event object.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SqsProcessingSelection {

  private static final ContextKey<SQSEvent> SELECTED_EVENT =
      ContextKey.named("aws-lambda-sqs-processing-event");

  public static boolean isSelected(Context context, SQSEvent event) {
    return context.get(SELECTED_EVENT) == event;
  }

  public static Context select(Context context, SQSEvent event) {
    return isSelected(context, event) ? context : context.with(SELECTED_EVENT, event);
  }

  private SqsProcessingSelection() {}
}
