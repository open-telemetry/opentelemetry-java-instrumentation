/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class TwitterUtilCoreInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public TwitterUtilCoreInstrumentationModule() {
    super("finagle-http", "finagle-http-23.11", "twitter-util-core");
  }

  @Override
  public String getModuleGroup() {
    return "netty";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LocalSchedulerActivationInstrumentation(), new PromiseMonitoredInstrumentation());
  }
}
