/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VaadinInstrumentationModule extends InstrumentationModule {
  // special flag set only when running tests
  private static final boolean TEST_MODE =
      Config.get().getBooleanProperty("otel.instrumentation.vaadin.test-mode", false);

  public VaadinInstrumentationModule() {
    super("vaadin", "vaadin-14.2");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in vaadin 14.2
    return hasClassesNamed("com.vaadin.flow.server.frontend.installer.NodeInstaller");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    List<TypeInstrumentation> instrumentations =
        asList(
            new VaadinServiceInstrumentation(),
            new RequestHandlerInstrumentation(),
            new UiInstrumentation(),
            new RouterInstrumentation(),
            new JavaScriptBootstrapUiInstrumentation(),
            new RpcInvocationHandlerInstrumentation(),
            new ClientCallableRpcInstrumentation());
    if (TEST_MODE) {
      instrumentations = new ArrayList<>(instrumentations);
      instrumentations.add(new NodeUpdaterInstrumentation());
    }
    return instrumentations;
  }
}
