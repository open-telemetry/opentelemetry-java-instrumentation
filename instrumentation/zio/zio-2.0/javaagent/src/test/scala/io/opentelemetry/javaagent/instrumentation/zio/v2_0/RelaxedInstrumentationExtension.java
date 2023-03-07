/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.AgentTestRunner;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class RelaxedInstrumentationExtension extends InstrumentationExtension {
  private RelaxedInstrumentationExtension() {
    super(AgentTestRunner.instance());
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    Context.root().makeCurrent();
  }

  public static RelaxedInstrumentationExtension create() {
    return new RelaxedInstrumentationExtension();
  }
}
