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

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkSerializer.serializeKey;

import java.util.Map;
import software.amazon.awssdk.core.SdkRequest;

public class UpdateItemRequest implements DbRequest {

  private SdkRequest sdkRequest;

  public UpdateItemRequest(SdkRequest sdkRequest) {
    this.sdkRequest = sdkRequest;
  }

  @Override
  public String statement() {
    StringBuilder builder = new StringBuilder("UPDATE");

    sdkRequest
        .getValueForField("TableName", String.class)
        .ifPresent(val -> builder.append(" Table: ").append(val));
    sdkRequest
        .getValueForField("UpdateExpression", String.class)
        .ifPresent(val -> builder.append(" Update: ").append(val));
    sdkRequest
        .getValueForField("Key", Map.class)
        .ifPresent(val -> builder.append(" Where: ").append(serializeKey(val)));
    sdkRequest
        .getValueForField("ConditionExpression", String.class)
        .ifPresent(val -> builder.append(" Where: ").append(val));

    return builder.toString();
  }
}
