/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.camel.CamelContext;

@AutoService(InstrumentationModule.class)
public class ApacheCamelInstrumentationModule extends InstrumentationModule {

  public ApacheCamelInstrumentationModule() {
    super("apache-camel", "apache-camel-2.20");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new CamelContextInstrumentation());
  }

  @Override
  public String[] additionalHelperClassNames() {
    return new String[] {"io.opentelemetry.extension.aws.AwsXrayPropagator"};
  }

  public static class CamelContextInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.apache.camel.CamelContext");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return not(isAbstract()).and(implementsInterface(named("org.apache.camel.CamelContext")));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(
          named("start").and(isPublic()).and(takesArguments(0)),
          ApacheCamelInstrumentationModule.class.getName() + "$ContextAdvice");
    }
  }

  public static class ContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onContextStart(@Advice.This final CamelContext context) throws Exception {

      if (context.hasService(CamelTracingService.class) == null) {
        // start this service eager so we init before Camel is starting up
        context.addService(new CamelTracingService(context), true, true);
      }
    }
  }
}
