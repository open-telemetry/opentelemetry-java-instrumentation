/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class ElasticJobExperimentalAttributeExtractor
    implements AttributesExtractor<ElasticJobProcessRequest, Void> {

  private static final AttributeKey<String> ELASTICJOB_JOB_NAME =
      AttributeKey.stringKey("scheduling.apache-elasticjob.job.name");
  private static final AttributeKey<String> ELASTICJOB_TASK_ID =
      AttributeKey.stringKey("scheduling.apache-elasticjob.task.id");
  private static final AttributeKey<Long> ELASTICJOB_ITEM =
      AttributeKey.longKey("scheduling.apache-elasticjob.item");
  private static final AttributeKey<Long> ELASTICJOB_SHARDING_TOTAL_COUNT =
      AttributeKey.longKey("scheduling.apache-elasticjob.sharding.total.count");
  private static final AttributeKey<String> ELASTICJOB_SHARDING_ITEM_PARAMETERS =
      AttributeKey.stringKey("scheduling.apache-elasticjob.sharding.item.parameters");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ElasticJobProcessRequest elasticJobProcessRequest) {
    attributes.put(ELASTICJOB_JOB_NAME, elasticJobProcessRequest.getJobName());
    attributes.put(ELASTICJOB_TASK_ID, elasticJobProcessRequest.getTaskId());
    attributes.put(ELASTICJOB_ITEM, elasticJobProcessRequest.getItem());
    attributes.put(ELASTICJOB_SHARDING_TOTAL_COUNT, elasticJobProcessRequest.getShardingTotalCount());
    if (elasticJobProcessRequest.getShardingItemParameters() != null) {
      attributes.put(ELASTICJOB_SHARDING_ITEM_PARAMETERS, elasticJobProcessRequest.getShardingItemParameters());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ElasticJobProcessRequest elasticJobProcessRequest,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
