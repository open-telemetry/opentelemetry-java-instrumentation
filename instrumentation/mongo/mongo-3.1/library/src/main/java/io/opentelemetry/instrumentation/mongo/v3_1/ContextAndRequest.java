/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.google.auto.value.AutoValue;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.context.Context;

@AutoValue
abstract class ContextAndRequest {

  static ContextAndRequest create(Context context, CommandStartedEvent request) {
    return new AutoValue_ContextAndRequest(context, request);
  }

  abstract Context getContext();

  abstract CommandStartedEvent getRequest();
}
