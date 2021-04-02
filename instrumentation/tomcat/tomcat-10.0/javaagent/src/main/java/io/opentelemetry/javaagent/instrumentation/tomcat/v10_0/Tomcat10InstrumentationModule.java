/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerInstrumentation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
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
    return hasClassesNamed("org.apache.coyote.Adapter", "org.apache.catalina.connector.Request");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    // Tomcat 10+ is verified by making sure Request has a methods returning
    // jakarta.servlet.ReadListener which is returned by getReadListener method on Tomcat 10+
    return Collections.singletonList(
        new TomcatServerHandlerInstrumentation(
            Tomcat10InstrumentationModule.class.getPackage().getName()
                + ".Tomcat10ServerHandlerAdvice",
            named("org.apache.coyote.Request")
                .and(declaresMethod(returns(named("jakarta.servlet.ReadListener"))))));
  }
}
