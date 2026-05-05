/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.metro.v2_2;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class MetroInstrumentationModule extends InstrumentationModule {

  public MetroInstrumentationModule() {
    super(
        "metro", expandDeprecatedNames("jaxws-2.0-metro-2.2|deprecated:jaxws-metro-2.2", "jaxws"));
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.2.0.1
    return hasClassesNamed("com.sun.xml.ws.api.pipe.ServerTubeAssemblerContext");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ServerTubeAssemblerContextInstrumentation(), new SoapFaultBuilderInstrumentation());
  }
}
