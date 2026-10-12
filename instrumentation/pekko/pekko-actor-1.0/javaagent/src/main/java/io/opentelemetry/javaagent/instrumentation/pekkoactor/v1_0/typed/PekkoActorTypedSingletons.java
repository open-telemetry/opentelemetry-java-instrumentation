/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;

public final class PekkoActorTypedSingletons {

  private static final ScopedThreadValue<UnstashScope> currentUnstashScope =
      new ScopedThreadValue<>();

  public static ScopedThreadValue<UnstashScope> currentUnstashScope() {
    return currentUnstashScope;
  }

  private PekkoActorTypedSingletons() {}
}
