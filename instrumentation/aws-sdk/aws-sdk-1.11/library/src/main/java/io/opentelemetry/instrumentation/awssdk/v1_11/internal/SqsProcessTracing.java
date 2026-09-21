/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import java.util.List;

/**
 * Coordinates SQS response traversal with framework processing.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SqsProcessTracing {

  /**
   * Disables raw process spans for a traced response list and its views. Framework instrumentation
   * must select the list before traversal, and only when it will instrument processing itself.
   * Unsupported or disabled frameworks leave raw tracing enabled by not selecting the list.
   *
   * <p>Selection does not follow message objects into copied lists or other responses.
   */
  public static void selectFrameworkProcessing(List<?> messages) {
    TracingList.selectFrameworkProcessing(messages);
  }

  private SqsProcessTracing() {}
}
