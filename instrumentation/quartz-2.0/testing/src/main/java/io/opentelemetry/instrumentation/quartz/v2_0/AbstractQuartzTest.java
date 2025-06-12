/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractQuartzTest {

  protected abstract void configureScheduler(Scheduler scheduler);

  private Scheduler scheduler;

  protected abstract InstrumentationExtension getTesting();

  @BeforeAll
  void startScheduler() throws Exception {
    scheduler = createScheduler("default");
    configureScheduler(scheduler);
    scheduler.start();
  }

  @AfterAll
  void stopScheduler() throws Exception {
    scheduler.shutdown();
  }

  @Test
  void successfulJob() throws Exception {
    Trigger trigger = newTrigger().build();

    JobDetail jobDetail = newJob().withIdentity("test", "jobs").ofType(SuccessfulJob.class).build();

    scheduler.scheduleJob(jobDetail, trigger);

    List<AttributeAssertion> assertions = codeFunctionAssertions(SuccessfulJob.class, "execute");
    assertions.add(equalTo(AttributeKey.stringKey("job.system"), "quartz"));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("jobs.test")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(assertions),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void failingJob() throws Exception {
    Trigger trigger = newTrigger().build();

    JobDetail jobDetail = newJob().withIdentity("fail", "jobs").ofType(FailingJob.class).build();

    scheduler.scheduleJob(jobDetail, trigger);

    List<AttributeAssertion> assertions = codeFunctionAssertions(FailingJob.class, "execute");
    assertions.add(equalTo(AttributeKey.stringKey("job.system"), "quartz"));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("jobs.fail")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalStateException("Bad job"))
                            .hasAttributesSatisfyingExactly(assertions)));
  }

  private static Scheduler createScheduler(String name) throws Exception {
    StdSchedulerFactory factory = new StdSchedulerFactory();
    Properties properties = new Properties();
    properties.load(AbstractQuartzTest.class.getResourceAsStream("/org/quartz/quartz.properties"));
    properties.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, name);
    factory.initialize(properties);
    return factory.getScheduler();
  }

  public static class SuccessfulJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
      GlobalOpenTelemetry.getTracer("test").spanBuilder("child").startSpan().end();
      // ensure that JobExecutionContext is serializable
      try {
        new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(context);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static class FailingJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
      throw new IllegalStateException("Bad job");
    }
  }
}
