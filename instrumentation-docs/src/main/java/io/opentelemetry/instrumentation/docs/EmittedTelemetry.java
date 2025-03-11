/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationScopeInfoDeserializer;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;

public class EmittedTelemetry {

  @JsonDeserialize(using = InstrumentationScopeInfoDeserializer.class)
  private InstrumentationScopeInfo instrumentationScopeInfo;

  public EmittedTelemetry() {}

  public EmittedTelemetry(InstrumentationScopeInfo scope) {
    this.instrumentationScopeInfo = scope;
  }

  public InstrumentationScopeInfo getScope() {
    return instrumentationScopeInfo;
  }

  public void setScope(InstrumentationScopeInfo scope) {
    this.instrumentationScopeInfo = scope;
  }
}
