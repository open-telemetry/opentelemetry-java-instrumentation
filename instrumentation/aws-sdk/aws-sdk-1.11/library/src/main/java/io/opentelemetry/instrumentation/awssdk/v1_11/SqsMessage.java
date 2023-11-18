/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import java.util.Map;

/**
 * A wrapper interface for {@link com.amazonaws.services.sqs.model.Message}. Using this wrapper
 * avoids muzzle failure when sqs classes are not present.
 */
interface SqsMessage {

  Map<String, String> getAttributes();

  String getMessageAttribute(String name);

  String getMessageId();
}
