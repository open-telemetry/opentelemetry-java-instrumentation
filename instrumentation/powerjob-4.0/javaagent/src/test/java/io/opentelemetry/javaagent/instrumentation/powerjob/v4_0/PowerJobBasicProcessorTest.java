/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.BASIC_PROCESSOR;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.sdk.BasicProcessor;

class PowerJobBasicProcessorTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();


  @Test
  void testCoreBasicProcessor() throws Exception {
    long jobId = 1;
    String jobParam = "abc";
    TaskContext taskContext = genTaskContext(jobId, jobParam);
    BasicProcessor testBasicProcessor = new TestBasicProcessor();
    testBasicProcessor.process(taskContext);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(String.format("%s.process", TestBasicProcessor.class.getSimpleName()));
                span.hasKind(SpanKind.INTERNAL);
                span.hasStatus(StatusData.unset());
                span.hasAttributesSatisfying(
                    attributeAssertions(
                        TestBasicProcessor.class.getName(), jobId, jobParam, BASIC_PROCESSOR));
              });
        });
  }

  private static TaskContext genTaskContext(long jobId, String jobParam){
    TaskContext taskContext = new TaskContext();
    taskContext.setJobId(jobId);
    taskContext.setJobParams(jobParam);
    return taskContext;
  }



  private static List<AttributeAssertion> attributeAssertions(
      String codeNamespace, long jobId, String jobParam, String jobType) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(AttributeKey.stringKey("code.namespace"), codeNamespace),
                equalTo(AttributeKey.stringKey("code.function"), "process"),
                equalTo(AttributeKey.stringKey("job.system"), "powerjob"),
                equalTo(AttributeKey.longKey("scheduling.powerjob.job.id"), jobId),
                equalTo(AttributeKey.stringKey("scheduling.powerjob.job.type"), jobType)));
    if (!StringUtils.isNullOrEmpty(jobParam)) {
      attributeAssertions.add(
          equalTo(AttributeKey.stringKey("scheduling.powerjob.job.param"), jobParam));
    }
    return attributeAssertions;
  }
}
