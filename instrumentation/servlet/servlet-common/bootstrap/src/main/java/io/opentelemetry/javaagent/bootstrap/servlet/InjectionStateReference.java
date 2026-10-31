/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.lang.ref.WeakReference;
import javax.annotation.Nullable;

public final class InjectionStateReference {
  private final WeakReference<InjectionState> reference;

  public InjectionStateReference(InjectionState state) {
    reference = new WeakReference<>(state);
  }

  @Nullable
  public InjectionState get() {
    return reference.get();
  }
}
