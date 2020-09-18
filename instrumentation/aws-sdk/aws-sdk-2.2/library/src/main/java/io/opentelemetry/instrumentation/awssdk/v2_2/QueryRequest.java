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

import software.amazon.awssdk.core.SdkRequest;

public class QueryRequest implements DbRequest {

  private SdkRequest sdkRequest;

  public QueryRequest(SdkRequest sdkRequest) {
    this.sdkRequest = sdkRequest;
  }

  @Override
  public String statement() {
    StringBuilder builder = new StringBuilder("QUERY");

    sdkRequest
        .getValueForField("TableName", String.class)
        .ifPresent(val -> builder.append(" From: ").append(val));
    sdkRequest
        .getValueForField("Select", String.class)
        .ifPresent(val -> builder.append(" Select: ").append(val));
    sdkRequest
        .getValueForField("KeyConditionExpression", String.class)
        .ifPresent(val -> builder.append(" Where: ").append(val));
    sdkRequest
        .getValueForField("FilterExpression", String.class)
        .ifPresent(val -> builder.append(" Filter: ").append(val));
    sdkRequest
        .getValueForField("Limit", Integer.class)
        .ifPresent(val -> builder.append(" Limit: ").append(val));

    return builder.toString();
  }
}
