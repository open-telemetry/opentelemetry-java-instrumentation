/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.integration.channel.interceptor.GlobalChannelInterceptorWrapper;

public class ApplicationContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.context.support.AbstractApplicationContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.springframework.context.support.AbstractApplicationContext"));
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
        ApplicationContextInstrumentation.class.getName() + "$PostProcessBeanFactoryAdvice");
  }

  public static class PostProcessBeanFactoryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) ConfigurableListableBeanFactory beanFactory) {
      if (beanFactory instanceof BeanDefinitionRegistry
          && !beanFactory.containsBean("otelGlobalChannelInterceptor")) {

        BeanDefinition globalChannelInterceptorBean =
            genericBeanDefinition(GlobalChannelInterceptorWrapper.class)
                .addConstructorArgValue(SpringIntegrationSingletons.interceptor())
                .addPropertyValue("patterns", SpringIntegrationSingletons.patterns())
                .getBeanDefinition();

        ((BeanDefinitionRegistry) beanFactory)
            .registerBeanDefinition("otelGlobalChannelInterceptor", globalChannelInterceptorBean);
      }
    }
  }
}
