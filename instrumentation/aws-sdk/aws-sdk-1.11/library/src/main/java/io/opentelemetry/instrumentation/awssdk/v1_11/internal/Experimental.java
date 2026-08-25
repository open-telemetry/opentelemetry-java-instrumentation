/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public class Experimental {

  @Nullable
  private static volatile BiConsumer<AwsSdkTelemetryBuilder, Boolean> setMessageCreateSpansEnabled;

  /**
   * Sets whether a producer "Create" span is emitted for each eligible entry in an SQS batch send.
   * An entry is eligible when it does not already contain a creation context and the AWS SDK
   * version supports the per-entry {@code AWSTraceHeader} system attribute.
   *
   * <p>This option only applies when the stable messaging semantic conventions are enabled. It is
   * enabled by default.
   *
   * @param builder the telemetry builder
   * @param messageCreateSpansEnabled {@code true} to emit per-message create spans
   */
  public static void setMessageCreateSpansEnabled(
      AwsSdkTelemetryBuilder builder, boolean messageCreateSpansEnabled) {
    if (setMessageCreateSpansEnabled != null) {
      setMessageCreateSpansEnabled.accept(builder, messageCreateSpansEnabled);
    }
  }

  public static void internalSetMessageCreateSpansEnabled(
      BiConsumer<AwsSdkTelemetryBuilder, Boolean> setMessageCreateSpansEnabled) {
    Experimental.setMessageCreateSpansEnabled = setMessageCreateSpansEnabled;
  }

  private Experimental() {}
}
