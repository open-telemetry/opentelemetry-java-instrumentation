/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.job;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class JobFactoryBeanInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // Spring Batch XML config
    return named("org.springframework.batch.core.configuration.xml.JobParserJobFactoryBean")
        // JSR-352 XML config
        .or(named("org.springframework.batch.core.jsr.configuration.xml.JobFactoryBean"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("getObject").and(isPublic()).and(takesArguments(0)),
        this.getClass().getName() + "$GetObjectAdvice");
  }

  public static class GetObjectAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.FieldValue(value = "jobExecutionListeners", readOnly = false)
            JobExecutionListener[] jobExecutionListeners) {
      ContextStore<JobExecution, ContextAndScope> executionContextStore =
          InstrumentationContext.get(JobExecution.class, ContextAndScope.class);
      JobExecutionListener tracingListener = new TracingJobExecutionListener(executionContextStore);
      if (jobExecutionListeners == null) {
        jobExecutionListeners = new JobExecutionListener[] {tracingListener};
      } else {
        JobExecutionListener[] newListeners =
            new JobExecutionListener[jobExecutionListeners.length + 1];
        newListeners[0] = tracingListener;
        System.arraycopy(jobExecutionListeners, 0, newListeners, 1, jobExecutionListeners.length);
        jobExecutionListeners = newListeners;
      }
    }
  }
}
