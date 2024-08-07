/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Tomcat7InstrumentationModule extends InstrumentationModule {

  public Tomcat7InstrumentationModule() {
    super("tomcat", "tomcat-7.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // does not match tomcat 10.0+
    return hasClassesNamed(
        "javax.servlet.http.HttpServletRequest", "org.apache.catalina.loader.Constants");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    // Tomcat 10+ is excluded by making sure Request does not have any methods returning
    // jakarta.servlet.ReadListener which is returned by getReadListener method on Tomcat 10+
    String packageName = Tomcat7InstrumentationModule.class.getPackage().getName();
    return singletonList(
        new TomcatServerHandlerInstrumentation(
            packageName + ".Tomcat7ServerHandlerAdvice",
            packageName + ".Tomcat7AttachResponseAdvice"));
  }
}
