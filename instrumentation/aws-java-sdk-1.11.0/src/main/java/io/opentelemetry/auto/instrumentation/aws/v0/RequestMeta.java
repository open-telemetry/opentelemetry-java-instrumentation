package io.opentelemetry.auto.instrumentation.aws.v0;

import com.amazonaws.handlers.HandlerContextKey;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import lombok.Data;

@Data
public class RequestMeta {
  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  public static final HandlerContextKey<SpanScopePair> SPAN_SCOPE_PAIR_CONTEXT_KEY =
      new HandlerContextKey<>("io.opentelemetry.auto.SpanScopePair");

  private String bucketName;
  private String queueUrl;
  private String queueName;
  private String streamName;
  private String tableName;
}
