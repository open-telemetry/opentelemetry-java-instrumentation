/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

class AwsExperimentalAttributes {
  public static final AttributeKey<String> AWS_AGENT = stringKey("aws.agent");
  public static final AttributeKey<String> AWS_SERVICE = stringKey("aws.service");
  public static final AttributeKey<String> AWS_OPERATION = stringKey("aws.operation");
  public static final AttributeKey<String> AWS_ENDPOINT = stringKey("aws.endpoint");
  public static final AttributeKey<String> AWS_BUCKET_NAME = stringKey("aws.bucket.name");
  public static final AttributeKey<String> AWS_QUEUE_URL = stringKey("aws.queue.url");
  public static final AttributeKey<String> AWS_QUEUE_NAME = stringKey("aws.queue.name");
  public static final AttributeKey<String> AWS_STREAM_NAME = stringKey("aws.stream.name");
  public static final AttributeKey<String> AWS_TABLE_NAME = stringKey("aws.table.name");
  public static final AttributeKey<String> AWS_REQUEST_ID = stringKey("aws.requestId");
}
