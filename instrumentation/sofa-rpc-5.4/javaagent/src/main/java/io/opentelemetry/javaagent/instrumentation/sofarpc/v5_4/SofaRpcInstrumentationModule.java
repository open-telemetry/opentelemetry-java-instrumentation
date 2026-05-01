/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SofaRpcInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SofaRpcInstrumentationModule() {
    super("sofa-rpc", "sofa-rpc-5.4");
  }

  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register(
        "META-INF/services/com.alipay.sofa.rpc.filter.Filter",
        "sofa-rpc-5.4/META-INF/com.alipay.sofa.rpc.filter.Filter");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 5.4.0
    return hasClassesNamed("com.alipay.sofa.rpc.transport.ClientHandler");
  }

  @Override
  public List<String> exposedClassNames() {
    return asList(
        "io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4.OpenTelemetryClientFilter",
        "io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4.OpenTelemetryServerFilter");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ResourceInjectingTypeInstrumentation());
  }
}
