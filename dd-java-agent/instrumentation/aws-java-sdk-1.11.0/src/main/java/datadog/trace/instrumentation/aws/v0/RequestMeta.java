package datadog.trace.instrumentation.aws.v0;

import com.amazonaws.handlers.HandlerContextKey;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import lombok.Data;

@Data
public class RequestMeta {
  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  public static final HandlerContextKey<AgentScope> SCOPE_CONTEXT_KEY =
      new HandlerContextKey<>("DatadogScope");

  private String bucketName;
  private String queueUrl;
  private String queueName;
  private String streamName;
  private String tableName;
}
