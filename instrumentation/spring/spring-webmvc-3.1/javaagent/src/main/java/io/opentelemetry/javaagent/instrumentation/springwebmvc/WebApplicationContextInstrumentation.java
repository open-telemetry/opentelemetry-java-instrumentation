/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * This instrumentation adds the HandlerMappingResourceNameFilter definition to the spring context
 * When the context is created, the filter will be added to the beginning of the filter chain.
 */
public class WebApplicationContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "org.springframework.context.support.AbstractApplicationContext",
        "org.springframework.web.context.WebApplicationContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.springframework.context.support.AbstractApplicationContext"))
        .and(implementsInterface(named("org.springframework.web.context.WebApplicationContext")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("postProcessBeanFactory"))
            .and(
                takesArgument(
                    0,
                    named(
                        "org.springframework.beans.factory.config.ConfigurableListableBeanFactory"))),
        WebApplicationContextInstrumentation.class.getName() + "$FilterInjectingAdvice");
  }

  public static class FilterInjectingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) ConfigurableListableBeanFactory beanFactory) {
      if (beanFactory instanceof BeanDefinitionRegistry
          && !beanFactory.containsBean("otelAutoDispatcherFilter")) {

        ((BeanDefinitionRegistry) beanFactory)
            .registerBeanDefinition(
                "otelAutoDispatcherFilter", new HandlerMappingResourceNameFilter.BeanDefinition());
      }
    }
  }
}
