/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class ElasticJobExperimentalAttributeExtractor
    implements AttributesExtractor<ElasticJobProcessRequest, Void> {

  private static final AttributeKey<String> ELASTICJOB_JOB_NAME =
      stringKey("scheduling.apache-elasticjob.job.name");
  private static final AttributeKey<String> ELASTICJOB_TASK_ID =
      stringKey("scheduling.apache-elasticjob.task.id");
  private static final AttributeKey<Long> ELASTICJOB_SHARDING_ITEM_INDEX =
      longKey("scheduling.apache-elasticjob.sharding.item.index");
  private static final AttributeKey<Long> ELASTICJOB_SHARDING_TOTAL_COUNT =
      longKey("scheduling.apache-elasticjob.sharding.total.count");
  private static final AttributeKey<String> ELASTICJOB_SHARDING_ITEM_PARAMETER =
      stringKey("scheduling.apache-elasticjob.sharding.item.parameter");
  private static final AttributeKey<String> ELASTICJOB_JOB_TYPE =
      stringKey("scheduling.apache-elasticjob.job.type");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ElasticJobProcessRequest elasticJobProcessRequest) {
    attributes.put(ELASTICJOB_JOB_NAME, elasticJobProcessRequest.getJobName());
    attributes.put(ELASTICJOB_TASK_ID, elasticJobProcessRequest.getTaskId());
    attributes.put(ELASTICJOB_SHARDING_ITEM_INDEX, elasticJobProcessRequest.getShardingItemIndex());
    attributes.put(
        ELASTICJOB_SHARDING_TOTAL_COUNT, elasticJobProcessRequest.getShardingTotalCount());
    attributes.put(ELASTICJOB_JOB_TYPE, elasticJobProcessRequest.getJobType().name());
    String shardingItemParameter = elasticJobProcessRequest.getShardingItemParameter();
    if (shardingItemParameter != null) {
      attributes.put(ELASTICJOB_SHARDING_ITEM_PARAMETER, shardingItemParameter);
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
