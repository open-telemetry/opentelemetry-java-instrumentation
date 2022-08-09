/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.job;

import static net.bytebuddy.matcher.ElementMatchers.isArray;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.jsr.configuration.xml.JobFactoryBean;

public class JobFactoryBeanInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // JSR-352 XML config
    return named("org.springframework.batch.core.jsr.configuration.xml.JobFactoryBean");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), this.getClass().getName() + "$InitAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("setJobExecutionListeners"))
            .and(takesArguments(1))
            .and(takesArgument(0, isArray())),
        this.getClass().getName() + "$SetListenersAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This JobFactoryBean jobFactory) {
      // this will trigger the advice below, which will make sure that the tracing listener is
      // registered even if the application never calls setJobExecutionListeners() directly
      jobFactory.setJobExecutionListeners(new Object[] {});
    }
  }

  @SuppressWarnings("unused")
  public static class SetListenersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) Object[] listeners) {
      VirtualField<JobExecution, ContextAndScope> executionVirtualField =
          VirtualField.find(JobExecution.class, ContextAndScope.class);
      JobExecutionListener tracingListener = new TracingJobExecutionListener(executionVirtualField);

      if (listeners == null) {
        listeners = new Object[] {tracingListener};
      } else {
        Object[] newListeners = new Object[listeners.length + 1];
        newListeners[0] = tracingListener;
        System.arraycopy(listeners, 0, newListeners, 1, listeners.length);
        listeners = newListeners;
      }
    }
  }
}
