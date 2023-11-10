/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.util.Map;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * A wrapper interface for {@link software.amazon.awssdk.services.sqs.model.Message}. Using this
 * wrapper avoids muzzle failure when sqs classes are not present.
 */
interface SqsMessage {

  Map<String, MessageAttributeValue> messageAttributes();

  Map<String, String> attributesAsStrings();

  String getMessageAttribute(String name);

  String getMessageId();
}
