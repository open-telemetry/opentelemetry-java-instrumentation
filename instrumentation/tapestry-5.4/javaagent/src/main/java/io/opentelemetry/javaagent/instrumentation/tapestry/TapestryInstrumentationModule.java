/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class TapestryInstrumentationModule extends InstrumentationModule {

  public TapestryInstrumentationModule() {
    super("tapestry", "tapestry-5.4");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in tapestry 5.4.0
    return hasClassesNamed("org.apache.tapestry5.Binding2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new InitializeActivePageNameInstrumentation(),
        new ComponentPageElementImplInstrumentation());
  }
}
