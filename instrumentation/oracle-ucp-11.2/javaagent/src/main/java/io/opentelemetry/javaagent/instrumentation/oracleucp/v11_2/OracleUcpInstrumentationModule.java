/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OracleUcpInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public OracleUcpInstrumentationModule() {
    super("oracle-ucp", "oracle-ucp-11.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new UniversalConnectionPoolInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
