/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import org.apache.shardingsphere.elasticjob.api.ShardingContext;

public final class ElasticJobProcessRequest {
  private final String jobName;
  private final String taskId;
  private final int shardingItemIndex;
  private final int shardingTotalCount;
  private final String shardingItemParameter;
  private final String jobType;
  private Class<?> userJobClass;
  private String userMethodName = "process";

  private ElasticJobProcessRequest(
      String jobName,
      String taskId,
      int shardingItemIndex,
      int shardingTotalCount,
      String shardingItemParameter,
      String jobType) {
    this.jobName = jobName;
    this.taskId = taskId;
    this.shardingItemIndex = shardingItemIndex;
    this.shardingTotalCount = shardingTotalCount;
    this.shardingItemParameter = emptyToNull(shardingItemParameter);
    this.jobType = jobType;
  }

  public static ElasticJobProcessRequest create(
      String jobName,
      String taskId,
      int shardingItemIndex,
      int shardingTotalCount,
      String shardingItemParameters,
      String jobType) {
    return new ElasticJobProcessRequest(
        jobName, taskId, shardingItemIndex, shardingTotalCount, shardingItemParameters, jobType);
  }

  private static String emptyToNull(String string) {
    return string == null || string.isEmpty() ? null : string;
  }

  public static ElasticJobProcessRequest createFromShardingContext(
      ShardingContext shardingContext,
      String jobType,
      Class<?> userJobClass,
      String userMethodName) {
    ElasticJobProcessRequest request =
        create(
            shardingContext.getJobName(),
            shardingContext.getTaskId(),
            shardingContext.getShardingItem(),
            shardingContext.getShardingTotalCount(),
            shardingContext.getShardingParameter(),
            jobType);
    request.userJobClass = userJobClass;
    request.userMethodName = userMethodName;
    return request;
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

  public String getShardingItemParameter() {
    return this.shardingItemParameter;
  }

  public Class<?> getUserJobClass() {
    return this.userJobClass;
  }

  public String getUserMethodName() {
    return this.userMethodName;
  }
}
