/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.log.internal;

import static java.util.Collections.emptyMap;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MdcContext extends HashMap<String, String> {
  private static final long serialVersionUID = 1L;

  // Keep the original values on the event, including when enrichment overwrites an application key.
  private final Map<String, String> applicationMdc;

  public MdcContext(@Nullable Map<String, String> mdc) {
    super(mdc == null ? emptyMap() : mdc);
    applicationMdc = mdc == null ? emptyMap() : getApplicationMdc(mdc);
  }

  public static Map<String, String> getApplicationMdc(Map<String, String> mdc) {
    return mdc instanceof MdcContext ? ((MdcContext) mdc).applicationMdc : mdc;
  }

  private Object writeReplace() {
    // Socket appenders must not require OpenTelemetry classes on the receiving side.
    return new HashMap<>(this);
  }
}
