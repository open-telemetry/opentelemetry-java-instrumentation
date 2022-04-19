/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_16.internal;

import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface ContextDataAccessor<T> {

  @Nullable
  Object getValue(T contextData, String key);

  void forEach(T contextData, BiConsumer<String, Object> action);
}
