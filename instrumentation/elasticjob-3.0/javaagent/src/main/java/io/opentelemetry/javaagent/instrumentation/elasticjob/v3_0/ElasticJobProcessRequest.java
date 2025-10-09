/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticjob.v3_0;

public final class ElasticJobProcessRequest {
  private String jobName;
  private String taskId;
  private int item;
  private int shardingTotalCount;
  private String shardingItemParameters;
  private boolean failed;
  private String jobType;
  private Class<?> userJobClass;
  private String userMethodName;

  public static ElasticJobProcessRequest create(
      String jobName,
      String taskId,
      int item,
      int shardingTotalCount,
      String shardingItemParameters,
      String jobType) {
    ElasticJobProcessRequest request = new ElasticJobProcessRequest();
    request.jobName = jobName;
    request.taskId = taskId;
    request.item = item;
    request.shardingTotalCount = shardingTotalCount;
    request.shardingItemParameters = shardingItemParameters;
    request.jobType = jobType;
    return request;
  }

  public static ElasticJobProcessRequest createWithUserJobInfo(
      String jobName,
      String taskId,
      int item,
      int shardingTotalCount,
      String shardingItemParameters,
      String jobType,
      Class<?> userJobClass,
      String userMethodName) {
    ElasticJobProcessRequest request = new ElasticJobProcessRequest();
    request.jobName = jobName;
    request.taskId = taskId;
    request.item = item;
    request.shardingTotalCount = shardingTotalCount;
    request.shardingItemParameters = shardingItemParameters;
    request.jobType = jobType;
    request.userJobClass = userJobClass;
    request.userMethodName = userMethodName;
    return request;
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

  public int getItem() {
    return this.item;
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
