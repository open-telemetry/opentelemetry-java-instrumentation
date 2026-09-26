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
   * Disables raw process spans for the exact traced response list passed to this method. The owner
   * must mark that response before traversal, and only when it will instrument processing itself.
   * Each eligible application traversal of the response can produce raw process spans. Sublist
   * views are not traced.
   *
   * <p>Ownership does not follow message objects into copied lists or other responses.
   */
  public static void markProcessingOwnedOutsideSqsSdk(List<?> messages) {
    TracingList.markProcessingOwnedOutsideSqsSdk(messages);
  }

  private SqsProcessTracing() {}
}
