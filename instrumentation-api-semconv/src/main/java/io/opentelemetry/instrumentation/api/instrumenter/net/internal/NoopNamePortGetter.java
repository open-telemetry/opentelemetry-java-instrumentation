/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import javax.annotation.Nullable;

enum NoopNamePortGetter implements FallbackNamePortGetter<Object> {
  INSTANCE;

  @Nullable
  @Override
  public String name(Object o) {
    return null;
  }

  @Nullable
  @Override
  public Integer port(Object o) {
    return null;
  }
}
