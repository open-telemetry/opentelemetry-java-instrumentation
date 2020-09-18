/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.awssdk.v2_2;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractAwsClientInstrumentation extends Instrumenter.Default {
  private static final String INSTRUMENTATION_NAME = "aws-sdk";

  public AbstractAwsClientInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".TracingExecutionInterceptor",
      packageName + ".TracingExecutionInterceptor$ScopeHolder",
      "io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkSerializer",
      "io.opentelemetry.instrumentation.awssdk.v2_2.DbRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.CreateTableRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.DeleteItemRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.DeleteTableRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.GetItemRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.PutItemRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.QueryRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.UpdateItemRequest",
      "io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdk",
      "io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkHttpClientTracer",
      "io.opentelemetry.instrumentation.awssdk.v2_2.RequestType",
      "io.opentelemetry.instrumentation.awssdk.v2_2.DbRequestFactory",
      "io.opentelemetry.instrumentation.awssdk.v2_2.SdkRequestDecorator",
      "io.opentelemetry.instrumentation.awssdk.v2_2.DbRequestDecorator",
      "io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor"
    };
  }
}
