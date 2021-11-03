/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.jpms;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.rmi.context.server.ContextDispatcher;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

/**
 * RMI server instrumentation class {@link ContextDispatcher} implements an internal interface that
 * is not exported by java module system. This is not allowed on jdk17. This instrumentation module
 * exposes JDK internal classes for RMI server instrumentation.
 */
@AutoService(InstrumentationModule.class)
public class RmiJpmsInstrumentationModule extends InstrumentationModule {

  public RmiJpmsInstrumentationModule() {
    super("rmi", "rmi-jpms");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {

      @Override
      public boolean matches(ClassLoader target) {
        // runs only in bootstrap class loader
        return JavaModule.isSupported() && target == null;
      }
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ExposeRmiModuleInstrumentation());
  }
}
