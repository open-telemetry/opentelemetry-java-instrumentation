/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class InjectionStateStore {
  // The request owns stream states through async writes; the stream only holds a weak reference.
  public static final String REQUEST_ATTRIBUTE = InjectionStateStore.class.getName();

  private final List<InjectionState> states = new ArrayList<>();

  public synchronized void add(InjectionState state) {
    states.add(state);
  }

  public static final class Reference extends WeakReference<InjectionState> {
    public Reference(InjectionState state) {
      super(state);
    }
  }
}
