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

import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;

public class DbRequestFactory {

  @Nullable
  public DbRequest valueOf(SdkRequest sdkRequest) {
    String className = sdkRequest.getClass().getSimpleName();
    switch (className) {
      case "QueryRequest":
        return new QueryRequest(sdkRequest);
      case "GetItemRequest":
        return new GetItemRequest(sdkRequest);
      case "DeleteItemRequest":
        return new DeleteItemRequest(sdkRequest);
      case "DeleteTableRequest":
        return new DeleteTableRequest(sdkRequest);
      case "UpdateItemRequest":
        return new UpdateItemRequest(sdkRequest);
      case "PutItemRequest":
        return new PutItemRequest(sdkRequest);
      case "CreateTableRequest":
        return new CreateTableRequest(sdkRequest);
    }
    return null;
  }
}
