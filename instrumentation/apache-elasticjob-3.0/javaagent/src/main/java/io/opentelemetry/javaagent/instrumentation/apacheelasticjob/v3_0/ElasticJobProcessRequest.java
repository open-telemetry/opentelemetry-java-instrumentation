/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import javax.annotation.Nullable;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;

public final class ElasticJobProcessRequest {
  private final String jobName;
  private final String taskId;
  private final int shardingItemIndex;
  private final int shardingTotalCount;
  @Nullable private final String shardingItemParameter;
  private final ElasticJobType jobType;
  private final Class<?> userJobClass;
  private final String userMethodName;

  private ElasticJobProcessRequest(
      ShardingContext shardingContext,
      ElasticJobType jobType,
      Class<?> userJobClass,
      String userMethodName) {
    this.jobName = shardingContext.getJobName();
    this.taskId = shardingContext.getTaskId();
    this.shardingItemIndex = shardingContext.getShardingItem();
    this.shardingTotalCount = shardingContext.getShardingTotalCount();
    this.shardingItemParameter = emptyToNull(shardingContext.getShardingParameter());
    this.jobType = jobType;
    this.userJobClass = userJobClass;
    this.userMethodName = userMethodName;
  }

  @Nullable
  private static String emptyToNull(@Nullable String string) {
    return string == null || string.isEmpty() ? null : string;
  }

  public static ElasticJobProcessRequest create(
      ShardingContext shardingContext,
      ElasticJobType jobType,
      Class<?> userJobClass,
      String userMethodName) {
    return new ElasticJobProcessRequest(shardingContext, jobType, userJobClass, userMethodName);
  }

  public String getJobName() {
    return this.jobName;
  }

  public String getTaskId() {
    return this.taskId;
  }

  public int getShardingItemIndex() {
    return this.shardingItemIndex;
  }

  public int getShardingTotalCount() {
    return this.shardingTotalCount;
  }

  @Nullable
  public String getShardingItemParameter() {
    return this.shardingItemParameter;
  }

  public ElasticJobType getJobType() {
    return this.jobType;
  }

  public Class<?> getUserJobClass() {
    return this.userJobClass;
  }

  public String getUserMethodName() {
    return this.userMethodName;
  }
}
