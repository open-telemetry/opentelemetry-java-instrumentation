/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.handlers.HandlerContextKey;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class RequestMeta {
  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  public static final HandlerContextKey<ContextScopePair> CONTEXT_SCOPE_PAIR_CONTEXT_KEY =
      new HandlerContextKey<>(RequestMeta.class.getName() + ".ContextScopePair");

  public static Builder builder() {
    return new AutoValue_RequestMeta.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setBucketName(String bucketName);

    public abstract Builder setQueueUrl(String queueUrl);

    public abstract Builder setStreamName(String streamName);

    public abstract Builder setTableName(String tableName);

    public abstract Builder setQueueName(String queueName);

    public abstract RequestMeta build();
  }

  @Nullable
  public abstract String getBucketName();

  @Nullable
  public abstract String getQueueUrl();

  @Nullable
  public abstract String getQueueName();

  @Nullable
  public abstract String getStreamName();

  @Nullable
  public abstract String getTableName();
}
