/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springweb;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;

/**
 * This instrumentation adds the OpenTelemetryHandlerMappingFilter definition to the spring context
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

  @SuppressWarnings("unused")
  public static class FilterInjectingAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) ConfigurableListableBeanFactory beanFactory) {
      if (beanFactory instanceof BeanDefinitionRegistry
          && !beanFactory.containsBean("otelAutoDispatcherFilter")) {
        try {
          // Firstly check whether DispatcherServlet is present. We need to load an instrumented
          // class from spring-webmvc to trigger injection that makes
          // OpenTelemetryHandlerMappingFilter available.
          beanFactory
              .getBeanClassLoader()
              .loadClass("org.springframework.web.servlet.DispatcherServlet");

          // Now attempt to load our injected instrumentation class.
          Class<?> clazz =
              beanFactory
                  .getBeanClassLoader()
                  .loadClass("org.springframework.web.servlet.OpenTelemetryHandlerMappingFilter");
          GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
          beanDefinition.setScope(SCOPE_SINGLETON);
          beanDefinition.setBeanClass(clazz);
          beanDefinition.setBeanClassName(clazz.getName());

          ((BeanDefinitionRegistry) beanFactory)
              .registerBeanDefinition("otelAutoDispatcherFilter", beanDefinition);
        } catch (ClassNotFoundException ignored) {
          // Ignore
        }
      }
    }
  }
}
