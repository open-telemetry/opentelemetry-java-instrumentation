/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VaadinInstrumentationModule extends InstrumentationModule {

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
    return asList(
        new VaadinServiceInstrumentation(),
        new RequestHandlerInstrumentation(),
        new UiInstrumentation(),
        new RouterInstrumentation(),
        new JavaScriptBootstrapUiInstrumentation(),
        new RpcInvocationHandlerInstrumentation(),
        new ClientCallableRpcInstrumentation());
  }
}
