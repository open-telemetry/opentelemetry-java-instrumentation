/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.IntervalTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.OneTimeTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.TaskWithError;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.TriggerTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.EnhancedClassTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.service.LambdaTaskConfigurer;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;

class SpringSchedulingTest {

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.spring-scheduling.experimental-span-attributes");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void scheduleOneTimeTest() throws Exception {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    cleanup.deferCleanup(scheduler::destroy);
    scheduler.initialize();

    OneTimeTask task = new OneTimeTask();
    scheduler.schedule(task, new Date()).get(5, SECONDS);
    assertThat(task.blockUntilExecute()).isTrue();
    scheduler.shutdown();
    assertThat(testing.waitForTraces(0)).isEmpty();
  }

  @Test
  void scheduleCronExpressionTest() throws InterruptedException {
    GenericApplicationContext context = createContext(TriggerTask.class);
    context.refresh();

    TriggerTask task = context.getBean(TriggerTask.class);
    assertThat(task.blockUntilExecute()).isTrue();

    assertThat(task).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TriggerTask.run")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_FUNCTION_NAME, TriggerTask.class.getName() + ".run"),
                            equalTo(stringKey("job.system"), experimental("spring_scheduling")))));
  }

  @Test
  void scheduleIntervalTest() throws InterruptedException {
    GenericApplicationContext context = createContext(IntervalTask.class);
    context.refresh();

    IntervalTask task = context.getBean(IntervalTask.class);
    assertThat(task.blockUntilExecute()).isTrue();

    assertThat(task).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("IntervalTask.run")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_FUNCTION_NAME, IntervalTask.class.getName() + ".run"),
                            equalTo(stringKey("job.system"), experimental("spring_scheduling")))));
  }

  @Test
  void scheduleLambdaTest() throws InterruptedException {
    GenericApplicationContext context = createContext(LambdaTaskConfigurer.class);
    context.refresh();

    LambdaTaskConfigurer configurer = context.getBean(LambdaTaskConfigurer.class);
    assertThat(configurer.singleUseLatch.await(2000, MILLISECONDS)).isTrue();

    assertThat(configurer).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("LambdaTaskConfigurer$$Lambda.run")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                CODE_FUNCTION_NAME,
                                val ->
                                    val.startsWith(
                                            LambdaTaskConfigurer.class.getName() + "$$Lambda")
                                        .endsWith("run")),
                            equalTo(stringKey("job.system"), experimental("spring_scheduling")))));
  }

  @Test
  void scheduleEnhancedClassTest() throws InterruptedException {
    assumeTrue(testLatestDeps());

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    cleanup.deferCleanup(() -> context.close());
    context.register(EnhancedClassTaskConfig.class);
    context.refresh();

    assertThat(context.getBean(EnhancedClassTaskConfig.class).getClass())
        .isNotEqualTo(EnhancedClassTaskConfig.class);

    CountDownLatch latch = context.getBean(CountDownLatch.class);
    assertThat(latch.await(5, SECONDS)).isTrue();

    assertThat(latch).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("EnhancedClassTaskConfig.run")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                EnhancedClassTaskConfig.class.getName() + ".run"),
                            equalTo(stringKey("job.system"), experimental("spring_scheduling")))));
  }

  @Test
  void taskWithErrorTest() throws InterruptedException {
    GenericApplicationContext context = createContext(TaskWithError.class);
    RootBeanDefinition scheduler = new RootBeanDefinition(ThreadPoolTaskScheduler.class);
    scheduler
        .getPropertyValues()
        .add(
            "errorHandler",
            (ErrorHandler) throwable -> GlobalTraceUtil.runWithSpan("error-handler", () -> {}));
    context.registerBeanDefinition("scheduler", scheduler);
    context.refresh();

    TaskWithError task = context.getBean(TaskWithError.class);
    assertThat(task.blockUntilExecute()).isTrue();

    assertThat(task).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("TaskWithError.run")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_FUNCTION_NAME, TaskWithError.class.getName() + ".run"),
                            equalTo(stringKey("job.system"), experimental("spring_scheduling")))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE, IllegalStateException.class.getName()),
                                        equalTo(EXCEPTION_MESSAGE, "failure"),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class)))),
                span -> span.hasName("error-handler").hasParent(trace.getSpan(0))));
  }

  private static GenericApplicationContext createContext(Class<?> beanClass) {
    GenericApplicationContext context = new GenericApplicationContext();
    cleanup.deferCleanup(() -> context.close());
    context.registerBeanDefinition(
        "schedulingProcessor", new RootBeanDefinition(ScheduledAnnotationBeanPostProcessor.class));
    context.registerBeanDefinition("task", new RootBeanDefinition(beanClass));
    return context;
  }

  private static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }
}
