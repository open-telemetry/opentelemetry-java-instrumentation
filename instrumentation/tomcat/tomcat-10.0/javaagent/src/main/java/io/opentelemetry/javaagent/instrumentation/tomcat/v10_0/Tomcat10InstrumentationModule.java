/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerInstrumentation;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Tomcat10InstrumentationModule extends InstrumentationModule {

  public Tomcat10InstrumentationModule() {
    super("tomcat", "tomcat-10.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // only matches tomcat 10.0+
    return hasClassesNamed("jakarta.servlet.ReadListener");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(
        new TomcatServerHandlerInstrumentation(
            Tomcat10InstrumentationModule.class.getPackage().getName()
                + ".Tomcat10ServerHandlerAdvice"));
  }
}
