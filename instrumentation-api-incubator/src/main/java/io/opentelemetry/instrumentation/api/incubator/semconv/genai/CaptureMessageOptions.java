/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CaptureMessageOptions {

  public abstract boolean captureMessageContent();

  public abstract boolean emitExperimentalConventions();

  public static CaptureMessageOptions create(
      boolean captureMessageContent, boolean emitExperimentalConventions) {
    return new AutoValue_CaptureMessageOptions(captureMessageContent, emitExperimentalConventions);
  }
}
