/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.xxljob;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.thread.JobThread;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractXxlJobTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testGlueJob() {
    JobThread jobThread = new JobThread(1, getGlueJobHandler());
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorTimeout(0);
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
    checkXxlJob(
        "CustomizedGroovyHandler.execute",
        StatusData.unset(),
        "execute",
        GlueTypeEnum.GLUE_GROOVY.getDesc(),
        "CustomizedGroovyHandler");
    jobThread.toStop("Test finish");
  }

  @Test
  void testScriptJob() {
    XxlJobFileAppender.initLogPath("resources/test/log");
    JobThread jobThread = new JobThread(2, getScriptJobHandler());
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorParams("");
    triggerParam.setExecutorTimeout(0);
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
    checkXxlJobWithoutNamespace(
        "GLUE(Shell).ID-2", StatusData.unset(), "ID-2", GlueTypeEnum.GLUE_SHELL.getDesc());
    jobThread.toStop("Test finish");
  }

  @Test
  void testSimpleJob() {
    JobThread jobThread = new JobThread(3, getCustomizeHandler());
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorTimeout(0);
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
    checkXxlJob(
        "SimpleCustomizedHandler.execute",
        StatusData.unset(),
        "execute",
        GlueTypeEnum.BEAN.getDesc(),
        getPackageName() + ".SimpleCustomizedHandler");
    jobThread.toStop("Test finish");
  }

  @Test
  public void testMethodJob() {
    JobThread jobThread = new JobThread(4, getMethodHandler());
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorTimeout(0);
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
    checkXxlJob(
        "ReflectObject.echo",
        StatusData.unset(),
        "echo",
        GlueTypeEnum.BEAN.getDesc(),
        "io.opentelemetry.instrumentation.xxljob.ReflectiveMethodsFactory$ReflectObject");
    jobThread.toStop("Test finish");
  }

  @Test
  void testFailedJob() {
    JobThread jobThread = new JobThread(5, getCustomizeFailedHandler());
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorTimeout(0);
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
    checkXxlJob(
        "CustomizedFailedHandler.execute",
        StatusData.error(),
        "execute",
        GlueTypeEnum.BEAN.getDesc(),
        getPackageName() + ".CustomizedFailedHandler");
    jobThread.toStop("Test finish");
  }

  protected abstract IJobHandler getGlueJobHandler();

  protected abstract IJobHandler getScriptJobHandler();

  protected abstract IJobHandler getCustomizeHandler();

  protected abstract IJobHandler getCustomizeFailedHandler();

  protected abstract IJobHandler getMethodHandler();

  protected abstract String getPackageName();

  private static void checkXxlJob(String spanName, StatusData statusData, String... attributes) {
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasKind(SpanKind.INTERNAL)
                    .hasName(spanName)
                    .hasStatus(statusData)
                    .hasAttributesSatisfying(
                        equalTo(AttributeKey.stringKey("job.system"), "xxl-job"),
                        equalTo(AttributeKey.stringKey("code.function"), attributes[0]),
                        equalTo(
                            AttributeKey.stringKey("scheduling.xxl-job.glue.type"), attributes[1]),
                        equalTo(AttributeKey.stringKey("code.namespace"), attributes[2]));
              });
        });
  }

  private static void checkXxlJobWithoutNamespace(
      String spanName, StatusData statusData, String... attributes) {
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasKind(SpanKind.INTERNAL)
                    .hasName(spanName)
                    .hasStatus(statusData)
                    .hasAttributesSatisfying(
                        equalTo(AttributeKey.stringKey("job.system"), "xxl-job"),
                        equalTo(AttributeKey.stringKey("code.function"), attributes[0]),
                        equalTo(
                            AttributeKey.stringKey("scheduling.xxl-job.glue.type"), attributes[1]));
              });
        });
  }
}
