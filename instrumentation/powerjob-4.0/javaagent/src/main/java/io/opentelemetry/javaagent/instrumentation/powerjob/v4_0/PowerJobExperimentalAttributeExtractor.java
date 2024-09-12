/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import tech.powerjob.worker.core.processor.ProcessResult;

class PowerJobExperimentalAttributeExtractor
    implements AttributesExtractor<PowerJobProcessRequest, ProcessResult> {

  private static final AttributeKey<Long> POWERJOB_JOB_ID =
      AttributeKey.longKey("scheduling.powerjob.job.id");
  private static final AttributeKey<String> POWERJOB_JOB_PARAM =
      AttributeKey.stringKey("scheduling.powerjob.job.param");
  private static final AttributeKey<String> POWERJOB_JOB_INSTANCE_PARAM =
      AttributeKey.stringKey("scheduling.powerjob.job.instance.param");
  private static final AttributeKey<String> POWERJOB_JOB_INSTANCE_TYPE =
      AttributeKey.stringKey("scheduling.powerjob.job.type");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      PowerJobProcessRequest powerJobProcessRequest) {
    attributes.put(POWERJOB_JOB_ID, powerJobProcessRequest.getJobId());
    attributes.put(POWERJOB_JOB_PARAM, powerJobProcessRequest.getJobParams());
    attributes.put(POWERJOB_JOB_INSTANCE_PARAM, powerJobProcessRequest.getInstanceParams());
    attributes.put(POWERJOB_JOB_INSTANCE_TYPE, powerJobProcessRequest.getJobType());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      PowerJobProcessRequest powerJobProcessRequest,
      @Nullable ProcessResult unused,
      @Nullable Throwable error) {}
}
