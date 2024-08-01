/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerInstrumentation;
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
    return hasClassesNamed("jakarta.servlet.http.HttpServletRequest")
        .and(
            // tomcat 10 has at least one of these two classes. Cache$EvictionOrder is present in
            // 10.0.0, but is removed before 10.1.0. GenericUser is added before Cache$EvictionOrder
            // is removed
            hasClassesNamed("org.apache.catalina.users.GenericUser")
                .or(hasClassesNamed("org.apache.catalina.webresources.Cache$EvictionOrder")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    String packageName = Tomcat10InstrumentationModule.class.getPackage().getName();
    return singletonList(
        new TomcatServerHandlerInstrumentation(
            packageName + ".Tomcat10ServerHandlerAdvice",
            packageName + ".Tomcat10AttachResponseAdvice"));
  }
}
