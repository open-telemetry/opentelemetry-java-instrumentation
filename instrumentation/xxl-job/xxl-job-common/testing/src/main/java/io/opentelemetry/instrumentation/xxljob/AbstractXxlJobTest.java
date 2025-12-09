/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.xxljob;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.thread.JobThread;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractXxlJobTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    XxlJobFileAppender.initLogPath("build/xxljob/log");
  }

  private void trigger(JobThread jobThread) {
    trigger(jobThread, null);
  }

  protected void trigger(JobThread jobThread, String executorParams) {
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorTimeout(0);
    if (executorParams != null) {
      triggerParam.setExecutorParams(executorParams);
    }
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
  }

  @Test
  void testGlueJob() {
    JobThread jobThread = new JobThread(1, getGlueJobHandler());
    trigger(jobThread);
    checkXxlJob(
        "CustomizedGroovyHandler.execute",
        StatusData.unset(),
        GlueTypeEnum.GLUE_GROOVY,
        "CustomizedGroovyHandler",
        "execute");
    jobThread.toStop("Test finish");
  }

  @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Shell scripts require /bin/sh")
  @Test
  void testScriptJob() {
    JobThread jobThread = new JobThread(2, getScriptJobHandler());
    trigger(jobThread, "");
    checkXxlJobWithoutCodeAttributes("GLUE(Shell)", StatusData.unset(), GlueTypeEnum.GLUE_SHELL, 2);
    jobThread.toStop("Test finish");
  }

  @Test
  void testSimpleJob() {
    JobThread jobThread = new JobThread(3, getCustomizeHandler());
    trigger(jobThread);
    checkXxlJob(
        "SimpleCustomizedHandler.execute",
        StatusData.unset(),
        GlueTypeEnum.BEAN,
        getPackageName() + ".SimpleCustomizedHandler",
        "execute");
    jobThread.toStop("Test finish");
  }

  protected Class<?> getReflectObjectClass() {
    return ReflectiveMethodsFactory.ReflectObject.class;
  }

  @Test
  public void testMethodJob() {
    // method handle is null if test is not supported by tested version of the library
    Assumptions.assumeTrue(getMethodHandler() != null);

    JobThread jobThread = new JobThread(4, getMethodHandler());
    trigger(jobThread);
    checkXxlJob(
        "ReflectObject.echo",
        StatusData.unset(),
        GlueTypeEnum.BEAN,
        getReflectObjectClass().getName(),
        "echo");
    jobThread.toStop("Test finish");
  }

  @Test
  void testFailedJob() {
    JobThread jobThread = new JobThread(5, getCustomizeFailedHandler());
    trigger(jobThread);
    checkXxlJob(
        "CustomizedFailedHandler.execute",
        StatusData.error(),
        GlueTypeEnum.BEAN,
        getPackageName() + ".CustomizedFailedHandler",
        "execute");
    jobThread.toStop("Test finish");
  }

  protected abstract IJobHandler getGlueJobHandler();

  protected abstract IJobHandler getScriptJobHandler();

  protected abstract IJobHandler getCustomizeHandler();

  protected abstract IJobHandler getCustomizeFailedHandler();

  protected abstract IJobHandler getMethodHandler();

  protected abstract String getPackageName();

  private static void checkXxlJob(
      String spanName, StatusData statusData, List<AttributeAssertion> assertions) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.INTERNAL)
                        .hasName(spanName)
                        .hasStatus(statusData)
                        .hasAttributesSatisfyingExactly(assertions)));
  }

  private static void checkXxlJob(
      String spanName,
      StatusData statusData,
      GlueTypeEnum glueType,
      String codeClass,
      String codeMethod) {
    List<AttributeAssertion> attributeAssertions = new ArrayList<>();
    attributeAssertions.addAll(attributeAssertions(glueType));
    attributeAssertions.addAll(
        SemconvCodeStabilityUtil.codeFunctionAssertions(codeClass, codeMethod));

    checkXxlJob(spanName, statusData, attributeAssertions);
  }

  private static void checkXxlJobWithoutCodeAttributes(
      String spanName, StatusData statusData, GlueTypeEnum glueType, int jobId) {
    List<AttributeAssertion> attributeAssertions = new ArrayList<>();
    attributeAssertions.addAll(attributeAssertions(glueType));
    attributeAssertions.add(equalTo(AttributeKey.longKey("scheduling.xxl-job.job.id"), jobId));

    checkXxlJob(spanName, statusData, attributeAssertions);
  }

  private static List<AttributeAssertion> attributeAssertions(GlueTypeEnum glueType) {
    return asList(
        equalTo(AttributeKey.stringKey("job.system"), "xxl-job"),
        equalTo(AttributeKey.stringKey("scheduling.xxl-job.glue.type"), glueType.getDesc()));
  }
}
