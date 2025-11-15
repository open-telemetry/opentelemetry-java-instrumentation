/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import org.apache.shardingsphere.elasticjob.api.ShardingContext;

public final class ElasticJobProcessRequest {
  private String jobName;
  private String taskId;
  private int shardingItemIndex;
  private int shardingTotalCount;
  private String shardingItemParameters;
  private boolean failed;
  private String jobType;
  private Class<?> userJobClass;
  private String userMethodName = "process";

  public static ElasticJobProcessRequest create(
      String jobName,
      String taskId,
      int shardingItemIndex,
      int shardingTotalCount,
      String shardingItemParameters,
      String jobType) {
    ElasticJobProcessRequest request = new ElasticJobProcessRequest();
    request.jobName = jobName;
    request.taskId = taskId;
    request.shardingItemIndex = shardingItemIndex;
    request.shardingTotalCount = shardingTotalCount;
    request.shardingItemParameters = shardingItemParameters;
    request.jobType = jobType;
    return request;
  }

  public static ElasticJobProcessRequest createWithUserJobInfo(
      String jobName,
      String taskId,
      int shardingItemIndex,
      int shardingTotalCount,
      String shardingItemParameters,
      String jobType,
      Class<?> userJobClass,
      String userMethodName) {
    ElasticJobProcessRequest request = new ElasticJobProcessRequest();
    request.jobName = jobName;
    request.taskId = taskId;
    request.shardingItemIndex = shardingItemIndex;
    request.shardingTotalCount = shardingTotalCount;
    request.shardingItemParameters = shardingItemParameters;
    request.jobType = jobType;
    request.userJobClass = userJobClass;
    request.userMethodName = userMethodName;
    return request;
  }

  public static ElasticJobProcessRequest createFromShardingContext(
      ShardingContext shardingContext,
      String jobType,
      Class<?> userJobClass,
      String userMethodName) {
    return createWithUserJobInfo(
        shardingContext.getJobName(),
        shardingContext.getTaskId(),
        shardingContext.getShardingItem(),
        shardingContext.getShardingTotalCount(),
        shardingContext.getShardingParameter(),
        jobType,
        userJobClass,
        userMethodName);
  }

  public void setFailed() {
    this.failed = true;
  }

  public boolean isFailed() {
    return this.failed;
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

  public String getShardingItemParameters() {
    return this.shardingItemParameters;
  }

  public String getJobType() {
    return this.jobType;
  }

  public boolean isScriptJob() {
    return "SCRIPT".equals(this.jobType);
  }

  public boolean isHttpJob() {
    return "HTTP".equals(this.jobType);
  }

  public Class<?> getUserJobClass() {
    return this.userJobClass;
  }

  public String getUserMethodName() {
    return this.userMethodName;
  }
}
