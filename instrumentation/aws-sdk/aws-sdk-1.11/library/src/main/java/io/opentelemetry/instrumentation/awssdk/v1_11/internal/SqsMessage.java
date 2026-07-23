/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import io.opentelemetry.context.Context;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A wrapper interface for {@link com.amazonaws.services.sqs.model.Message}. Using this wrapper
 * avoids muzzle failure when sqs classes are not present.
 */
interface SqsMessage {

  Context getCreationContext();

  Map<String, String> getAttributes();

  @Nullable
  String getMessageAttribute(String name);

  String getMessageId();
}
