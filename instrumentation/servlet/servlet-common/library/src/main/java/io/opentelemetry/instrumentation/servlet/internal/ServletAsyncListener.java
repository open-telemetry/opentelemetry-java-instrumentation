/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface ServletAsyncListener<RESPONSE> {
  void onComplete(@Nullable RESPONSE response);

  void onTimeout(long timeout);

  void onError(@Nullable Throwable throwable, @Nullable RESPONSE response);
}
