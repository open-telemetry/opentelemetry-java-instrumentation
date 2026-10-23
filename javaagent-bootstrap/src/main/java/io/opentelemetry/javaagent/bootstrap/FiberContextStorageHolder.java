/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import javax.annotation.Nullable;

public final class FiberContextStorageHolder {

  @SuppressWarnings("ThreadLocalUsage")
  private static final ThreadLocal<Activation> CURRENT = new ThreadLocal<>();

  private static volatile boolean enabled;

  public static boolean isEnabled() {
    return enabled;
  }

  @Nullable
  public static ContextState current() {
    Activation activation = CURRENT.get();
    return activation == null ? null : activation.contextState;
  }

  public static Activation activate(
      ContextState contextState, @Nullable Activation previousFiberActivation) {
    if (!enabled) {
      enabled = true;
    }
    Activation activation = new Activation(contextState, CURRENT.get(), previousFiberActivation);
    CURRENT.set(activation);
    return activation;
  }

  @Nullable
  public static Activation deactivate(Activation activation) {
    if (CURRENT.get() != activation) {
      throw new IllegalStateException("Fiber context activations must be closed in reverse order");
    }
    if (activation.previousGlobalActivation == null) {
      CURRENT.set(null);
    } else {
      CURRENT.set(activation.previousGlobalActivation);
    }
    return activation.previousFiberActivation;
  }

  private FiberContextStorageHolder() {}

  public interface ContextState {

    Object getContext();

    void setContext(Object context);

    @Nullable
    Object getScope();

    void setScope(@Nullable Object scope);
  }

  public static final class Activation {

    private final ContextState contextState;
    @Nullable private final Activation previousGlobalActivation;
    @Nullable private final Activation previousFiberActivation;

    private Activation(
        ContextState contextState,
        @Nullable Activation previousGlobalActivation,
        @Nullable Activation previousFiberActivation) {
      this.contextState = contextState;
      this.previousGlobalActivation = previousGlobalActivation;
      this.previousFiberActivation = previousFiberActivation;
    }
  }
}
