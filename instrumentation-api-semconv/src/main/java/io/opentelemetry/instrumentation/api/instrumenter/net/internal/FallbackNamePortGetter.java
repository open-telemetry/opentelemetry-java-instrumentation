/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface FallbackNamePortGetter<REQUEST> {

  @Nullable
  String name(REQUEST request);

  @Nullable
  Integer port(REQUEST request);

  @SuppressWarnings("unchecked")
  static <REQUEST> FallbackNamePortGetter<REQUEST> noop() {
    return (FallbackNamePortGetter<REQUEST>) NoopNamePortGetter.INSTANCE;
  }
}
