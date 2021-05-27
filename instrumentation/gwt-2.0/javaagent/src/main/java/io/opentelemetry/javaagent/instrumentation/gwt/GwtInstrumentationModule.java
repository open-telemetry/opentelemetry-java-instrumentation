/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class GwtInstrumentationModule extends InstrumentationModule {

  public GwtInstrumentationModule() {
    super("gwt", "gwt-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in gwt 2.0
    return hasClassesNamed("com.google.gwt.uibinder.client.UiBinder");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new GwtRpcInstrumentation());
  }
}
