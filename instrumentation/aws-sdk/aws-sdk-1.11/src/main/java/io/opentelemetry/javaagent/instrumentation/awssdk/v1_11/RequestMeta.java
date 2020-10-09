/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.handlers.HandlerContextKey;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import java.util.Objects;

public class RequestMeta {
  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  public static final HandlerContextKey<SpanWithScope> SPAN_SCOPE_PAIR_CONTEXT_KEY =
      new HandlerContextKey<>("io.opentelemetry.auto.SpanWithScope");

  private String bucketName;
  private String queueUrl;
  private String queueName;
  private String streamName;
  private String tableName;

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(String bucketName) {
    this.bucketName = bucketName;
  }

  public String getQueueUrl() {
    return queueUrl;
  }

  public void setQueueUrl(String queueUrl) {
    this.queueUrl = queueUrl;
  }

  public String getQueueName() {
    return queueName;
  }

  public void setQueueName(String queueName) {
    this.queueName = queueName;
  }

  public String getStreamName() {
    return streamName;
  }

  public void setStreamName(String streamName) {
    this.streamName = streamName;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RequestMeta that = (RequestMeta) o;
    return Objects.equals(bucketName, that.bucketName)
        && Objects.equals(queueUrl, that.queueUrl)
        && Objects.equals(queueName, that.queueName)
        && Objects.equals(streamName, that.streamName)
        && Objects.equals(tableName, that.tableName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bucketName, queueUrl, queueName, streamName, tableName);
  }
}
