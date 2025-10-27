/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.appender.v1_1;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JbossLogmanagerInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public JbossLogmanagerInstrumentationModule() {
    super("jboss-logmanager-appender", "jboss-logmanager-appender-1.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JbossLogmanagerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
