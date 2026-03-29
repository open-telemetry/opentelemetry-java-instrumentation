/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.BEDROCK_RUNTIME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.DYNAMODB;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.KINESIS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.LAMBDA;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.S3;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.SECRETSMANAGER;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.SNS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.SQS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.STEP_FUNCTIONS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.request;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.response;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;

/**
 * Temporary solution - maps only DynamoDB attributes. Final solution should be generated from AWS
 * SDK automatically
 * (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2291).
 */
// We match the actual name in the AWS SDK for better consistency with it and possible future
// autogeneration.
@SuppressWarnings("MemberName")
enum AwsSdkRequest {
  // generic requests
  DynamoDbRequest(DYNAMODB, "DynamoDbRequest"),
  S3Request(S3, "S3Request"),
  SnsRequest(SNS, "SnsRequest"),
  SqsRequest(SQS, "SqsRequest"),
  KinesisRequest(KINESIS, "KinesisRequest"),
  LambdaRequest(LAMBDA, "LambdaRequest"),
  SecretsManagerRequest(SECRETSMANAGER, "SecretsManagerRequest"),
  StepFunctionsRequest(STEP_FUNCTIONS, "SfnRequest"),
  // specific requests
  BatchGetItem(
      DYNAMODB,
      "dynamodb.model.BatchGetItemRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "RequestItems"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity")),
  BatchWriteItem(
      DYNAMODB,
      "dynamodb.model.BatchWriteItemRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "RequestItems"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity"),
      response(stringKey("aws.dynamodb.item_collection_metrics"), "ItemCollectionMetrics")),
  CreateTable(
      DYNAMODB,
      "dynamodb.model.CreateTableRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      request(stringArrayKey("aws.dynamodb.global_secondary_indexes"), "GlobalSecondaryIndexes"),
      request(stringArrayKey("aws.dynamodb.local_secondary_indexes"), "LocalSecondaryIndexes"),
      request(
          doubleKey("aws.dynamodb.provisioned_read_capacity"),
          "ProvisionedThroughput.ReadCapacityUnits"),
      request(
          doubleKey("aws.dynamodb.provisioned_write_capacity"),
          "ProvisionedThroughput.WriteCapacityUnits"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity"),
      response(stringKey("aws.dynamodb.item_collection_metrics"), "ItemCollectionMetrics")),
  DeleteItem(
      DYNAMODB,
      "dynamodb.model.DeleteItemRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity"),
      response(stringKey("aws.dynamodb.item_collection_metrics"), "ItemCollectionMetrics")),
  DeleteTable(
      DYNAMODB,
      "dynamodb.model.DeleteTableRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName")),
  DescribeTable(
      DYNAMODB,
      "dynamodb.model.DescribeTableRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName")),
  GetItem(
      DYNAMODB,
      "dynamodb.model.GetItemRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      request(stringKey("aws.dynamodb.projection"), "ProjectionExpression"),
      request(booleanKey("aws.dynamodb.consistent_read"), "ConsistentRead"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity")),
  ListTables(
      DYNAMODB,
      "dynamodb.model.ListTablesRequest",
      request(stringKey("aws.dynamodb.exclusive_start_table"), "ExclusiveStartTableName"),
      response(longKey("aws.dynamodb.table_count"), "TableNames"),
      request(longKey("aws.dynamodb.limit"), "Limit")),
  PutItem(
      DYNAMODB,
      "dynamodb.model.PutItemRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity"),
      response(stringKey("aws.dynamodb.item_collection_metrics"), "ItemCollectionMetrics")),
  Query(
      DYNAMODB,
      "dynamodb.model.QueryRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      request(stringArrayKey("aws.dynamodb.attributes_to_get"), "AttributesToGet"),
      request(booleanKey("aws.dynamodb.consistent_read"), "ConsistentRead"),
      request(stringKey("aws.dynamodb.index_name"), "IndexName"),
      request(longKey("aws.dynamodb.limit"), "Limit"),
      request(stringKey("aws.dynamodb.projection"), "ProjectionExpression"),
      request(booleanKey("aws.dynamodb.scan_forward"), "ScanIndexForward"),
      request(stringKey("aws.dynamodb.select"), "Select"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity")),
  Scan(
      DYNAMODB,
      "dynamodb.model.ScanRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      request(stringArrayKey("aws.dynamodb.attributes_to_get"), "AttributesToGet"),
      request(booleanKey("aws.dynamodb.consistent_read"), "ConsistentRead"),
      request(stringKey("aws.dynamodb.index_name"), "IndexName"),
      request(longKey("aws.dynamodb.limit"), "Limit"),
      request(stringKey("aws.dynamodb.projection"), "ProjectionExpression"),
      request(longKey("aws.dynamodb.segment"), "Segment"),
      request(stringKey("aws.dynamodb.select"), "Select"),
      request(longKey("aws.dynamodb.total_segments"), "TotalSegments"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity"),
      response(longKey("aws.dynamodb.count"), "Count"),
      response(longKey("aws.dynamodb.scanned_count"), "ScannedCount")),
  UpdateItem(
      DYNAMODB,
      "dynamodb.model.UpdateItemRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity"),
      response(stringKey("aws.dynamodb.item_collection_metrics"), "ItemCollectionMetrics")),
  UpdateTable(
      DYNAMODB,
      "dynamodb.model.UpdateTableRequest",
      request(stringArrayKey("aws.dynamodb.table_names"), "TableName"),
      request(stringArrayKey("aws.dynamodb.attribute_definitions"), "AttributeDefinitions"),
      request(
          stringArrayKey("aws.dynamodb.global_secondary_index_updates"),
          "GlobalSecondaryIndexUpdates"),
      request(
          doubleKey("aws.dynamodb.provisioned_read_capacity"),
          "ProvisionedThroughput.ReadCapacityUnits"),
      request(
          doubleKey("aws.dynamodb.provisioned_write_capacity"),
          "ProvisionedThroughput.WriteCapacityUnits"),
      response(stringArrayKey("aws.dynamodb.consumed_capacity"), "ConsumedCapacity")),
  ConverseRequest(
      BEDROCK_RUNTIME,
      "bedrockruntime.model.ConverseRequest",
      request(stringKey("gen_ai.request.model"), "modelId"));

  private final AwsSdkRequestType type;
  private final String requestClass;

  // Wrap in unmodifiableMap
  @SuppressWarnings("ImmutableEnumChecker")
  private final Map<FieldMapping.Type, List<FieldMapping>> fields;

  AwsSdkRequest(AwsSdkRequestType type, String requestClass, FieldMapping... fields) {
    this.type = type;
    this.requestClass = requestClass;
    this.fields = Collections.unmodifiableMap(FieldMapping.groupByType(fields));
  }

  @Nullable
  static AwsSdkRequest ofSdkRequest(SdkRequest request) {
    // try request type
    AwsSdkRequest result = ofType(request.getClass().getName());
    // try parent - generic
    if (result == null) {
      result = ofType(request.getClass().getSuperclass().getName());
    }
    return result;
  }

  private static AwsSdkRequest ofType(String typeName) {
    for (AwsSdkRequest type : values()) {
      if (typeName.endsWith(type.requestClass)) {
        return type;
      }
    }
    return null;
  }

  List<FieldMapping> fields(FieldMapping.Type type) {
    return fields.get(type);
  }

  AwsSdkRequestType type() {
    return type;
  }
}
