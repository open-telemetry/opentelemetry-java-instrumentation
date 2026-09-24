/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;

/**
 * Coordinates SQS response traversal with processing owned outside the raw SDK.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SqsProcessTracing {

  private static final VirtualField<List, Boolean> PROCESSING_OWNERSHIP =
      VirtualField.find(List.class, Boolean.class);

  /**
   * Disables raw process spans for the exact traced response list or list view passed to this
   * method. The owner must mark that object before traversal, and only when it will instrument
   * processing itself. Views have independent ownership and must be marked separately. Otherwise,
   * leave raw tracing enabled.
   *
   * <p>Ownership does not follow message objects into copied lists or other responses.
   */
  public static void markProcessingOwnedOutsideSqsSdk(List<?> messages) {
    PROCESSING_OWNERSHIP.set(TracingList.processingOwner(messages), true);
  }

  static boolean isProcessingOwnedOutsideSqsSdk(List<?> messages) {
    return PROCESSING_OWNERSHIP.get(messages) != null;
  }

  private SqsProcessTracing() {}
}
