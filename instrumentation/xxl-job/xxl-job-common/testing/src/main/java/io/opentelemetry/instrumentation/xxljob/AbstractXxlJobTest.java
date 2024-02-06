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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractXxlJobTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testGlueJob() {
    JobThread jobThread = new JobThread(1, getGlueJobHandler());
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorTimeout(0);
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
    checkXxlJob(
        "CustomizedGroovyHandler.execute",
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
    checkXxlJobWithoutNamespace("GLUE(Shell).ID-2", "ID-2", GlueTypeEnum.GLUE_SHELL.getDesc());
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
        "execute",
        GlueTypeEnum.BEAN.getDesc(),
        getPackageName() + ".SimpleCustomizedHandler");
    jobThread.toStop("Test finish");
  }

  @Test
  public void testMethodJob() {
    JobThread jobThread = new JobThread(0, getMethodHandler());
    TriggerParam triggerParam = new TriggerParam();
    triggerParam.setExecutorTimeout(0);
    jobThread.pushTriggerQueue(triggerParam);
    jobThread.start();
    checkXxlJob(
        "ReflectObject.echo",
        "echo",
        GlueTypeEnum.BEAN.getDesc(),
        "io.opentelemetry.instrumentation.xxljob.ReflectiveMethodsFactory$ReflectObject");
    jobThread.toStop("Test finish");
  }

  protected abstract String getPackageName();

  protected abstract IJobHandler getGlueJobHandler();

  protected abstract IJobHandler getScriptJobHandler();

  protected abstract IJobHandler getCustomizeHandler();

  protected abstract IJobHandler getMethodHandler();

  private static void checkXxlJob(String spanName, String... attributes) {
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasKind(SpanKind.INTERNAL)
                    .hasName(spanName)
                    .hasAttributesSatisfying(
                        equalTo(AttributeKey.stringKey("job.system"), "xxl-job"),
                        equalTo(AttributeKey.stringKey("scheduling.xxl-job.result.status"), "true"),
                        equalTo(AttributeKey.stringKey("code.function"), attributes[0]),
                        equalTo(
                            AttributeKey.stringKey("scheduling.xxl-job.glue.type"), attributes[1]),
                        equalTo(AttributeKey.stringKey("code.namespace"), attributes[2]));
              });
        });
  }

  private static void checkXxlJobWithoutNamespace(String spanName, String... attributes) {
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasKind(SpanKind.INTERNAL)
                    .hasName(spanName)
                    .hasAttributesSatisfying(
                        equalTo(AttributeKey.stringKey("job.system"), "xxl-job"),
                        equalTo(AttributeKey.stringKey("scheduling.xxl-job.result.status"), "true"),
                        equalTo(AttributeKey.stringKey("code.function"), attributes[0]),
                        equalTo(
                            AttributeKey.stringKey("scheduling.xxl-job.glue.type"), attributes[1]));
              });
        });
  }
}
