/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.rmi.context.client.RmiClientContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.rmi.context.server.RmiServerContextInstrumentation;
import java.rmi.Remote;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.utility.JavaModule;

@AutoService(InstrumentationModule.class)
public class RmiContextPropagationInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public RmiContextPropagationInstrumentationModule() {
    super("rmi", "rmi-context-propagation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RmiClientContextInstrumentation(), new RmiServerContextInstrumentation());
  }

  @Override
  public Map<JavaModule, List<String>> jpmsModulesToOpen() {
    return Collections.singletonMap(
        JavaModule.ofType(Remote.class), Arrays.asList("sun.rmi.server", "sun.rmi.transport"));
  }
}
