/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EmittedScope {
  @JsonDeserialize(using = InstrumentationScopeInfoDeserializer.class)
  private InstrumentationScopeInfo instrumentationScopeInfo;

  public EmittedScope() {}

  public InstrumentationScopeInfo getScope() {
    return instrumentationScopeInfo;
  }
}
