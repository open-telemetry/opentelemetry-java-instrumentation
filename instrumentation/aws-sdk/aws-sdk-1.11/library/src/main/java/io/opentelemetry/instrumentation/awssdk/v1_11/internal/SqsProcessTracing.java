/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import java.util.List;

/**
 * Coordinates SQS response traversal with processing owned outside the raw SDK.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SqsProcessTracing {

  /**
   * Disables raw process spans for a traced response list and its views. The owner must mark the
   * list before traversal, and only when it will instrument processing itself. Otherwise, leave raw
   * tracing enabled.
   *
   * <p>Ownership does not follow message objects into copied lists or other responses.
   */
  public static void markProcessingOwnedOutsideSqsSdk(List<?> messages) {
    TracingList.markProcessingOwnedOutsideSqsSdk(messages);
  }

  private SqsProcessTracing() {}
}
