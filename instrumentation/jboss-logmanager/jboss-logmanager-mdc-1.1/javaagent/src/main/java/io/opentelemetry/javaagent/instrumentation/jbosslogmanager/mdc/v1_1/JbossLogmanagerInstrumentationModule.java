/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.mdc.v1_1;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JbossLogmanagerInstrumentationModule extends InstrumentationModule {

  public JbossLogmanagerInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "jboss-logmanager-mdc-1.1" : "jboss-logmanager-mdc",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"jboss-logmanager-mdc", "jboss-logmanager"}
            : new String[] {"jboss-logmanager-mdc-1.1"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new JbossLoggerInstrumentation(), new JbossExtLogRecordInstrumentation());
  }
}
