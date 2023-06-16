/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import software.amazon.awssdk.core.SdkRequest;

/**
 * Reflective access to aws-sdk-java-sqs class ReceiveMessageRequest for points where we are not
 * sure whether SQS is on the classpath.
 */
final class SqsSendMessageRequestAccess {
  static boolean isInstance(SdkRequest request) {
    return request
        .getClass()
        .getName()
        .equals("software.amazon.awssdk.services.sqs.model.SendMessageRequest");
  }

  private SqsSendMessageRequestAccess() {}
}
