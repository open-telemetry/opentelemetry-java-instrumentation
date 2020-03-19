/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.awssdk.v1_11;

import com.amazonaws.handlers.HandlerContextKey;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import lombok.Data;

@Data
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
}
