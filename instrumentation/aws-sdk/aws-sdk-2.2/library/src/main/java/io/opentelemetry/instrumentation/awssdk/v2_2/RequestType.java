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

enum RequestType {
  S3("S3Request", "Bucket"),
  SQS("SqsRequest", "QueueUrl", "QueueName"),
  Kinesis("KinesisRequest", "StreamName"),
  DynamoDB("DynamoDbRequest", "TableName");

  private final String requestClass;
  private final String[] fields;

  RequestType(String requestClass, String... fields) {
    this.requestClass = requestClass;
    this.fields = fields;
  }

  String[] getFields() {
    return fields;
  }

  @Nullable
  static RequestType ofSdkRequest(SdkRequest request) {
    // exact request class should be 1st level child of request type
    String typeName =
        (request.getClass().getSuperclass() == null
                ? request.getClass()
                : request.getClass().getSuperclass())
            .getSimpleName();
    for (RequestType type : values()) {
      if (type.requestClass.equals(typeName)) {
        return type;
      }
    }
    return null;
  }
}
