/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.IntervalTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.OneTimeTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.TaskWithError;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.TriggerTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.EnhancedClassTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.IntervalTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.LambdaTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.OneTimeTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.TaskWithErrorConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.TriggerTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.service.LambdaTaskConfigurer;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SpringSchedulingTest {

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.spring-scheduling.experimental-span-attributes");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void scheduleOneTimeTest() throws InterruptedException {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(OneTimeTaskConfig.class);
    cleanup.deferCleanup(context);

    OneTimeTask task = context.getBean(OneTimeTask.class);
    task.blockUntilExecute();

    assertThat(task).isNotNull();
    assertThat(testing.waitForTraces(0)).isEmpty();
  }

  @Test
  void scheduleCronExpressionTest() throws InterruptedException {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TriggerTaskConfig.class);
    cleanup.deferCleanup(context);

    TriggerTask task = context.getBean(TriggerTask.class);
    task.blockUntilExecute();

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
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(IntervalTaskConfig.class);
    cleanup.deferCleanup(context);

    IntervalTask task = context.getBean(IntervalTask.class);
    task.blockUntilExecute();

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
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(LambdaTaskConfig.class);
    cleanup.deferCleanup(context);

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
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(EnhancedClassTaskConfig.class);
    cleanup.deferCleanup(context);

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
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TaskWithErrorConfig.class);
    cleanup.deferCleanup(context);

    TaskWithError task = context.getBean(TaskWithError.class);
    task.blockUntilExecute();

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

  private static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }
}
