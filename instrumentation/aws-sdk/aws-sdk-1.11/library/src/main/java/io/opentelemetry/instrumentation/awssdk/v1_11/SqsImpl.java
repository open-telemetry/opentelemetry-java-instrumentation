/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

final class SqsImpl {
  static {
    // Force loading of SqsClient; this ensures that an exception is thrown at this point when the
    // SQS library is not present, which will cause SqsAccess to have enabled=false in library mode.
    @SuppressWarnings("unused")
    String ensureLoadedDummy = AmazonSQS.class.getName();
  }

  private SqsImpl() {}

  public static boolean isReceiveMessageRequest(AmazonWebServiceRequest originalRequest) {
    return originalRequest instanceof ReceiveMessageRequest;
  }
}
