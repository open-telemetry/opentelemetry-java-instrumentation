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
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.RDS_DATA;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.S3;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.SECRETSMANAGER;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.SNS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.SQS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequestType.STEP_FUNCTIONS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.request;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.response;

import io.opentelemetry.api.common.AttributeKey;
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
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "RequestItems"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity")),
  BatchWriteItem(
      DYNAMODB,
      "dynamodb.model.BatchWriteItemRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "RequestItems"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity"),
      response(AttributeKeys.AWS_DYNAMODB_ITEM_COLLECTION_METRICS, "ItemCollectionMetrics")),
  CreateTable(
      DYNAMODB,
      "dynamodb.model.CreateTableRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      request(AttributeKeys.AWS_DYNAMODB_GLOBAL_SECONDARY_INDEXES, "GlobalSecondaryIndexes"),
      request(AttributeKeys.AWS_DYNAMODB_LOCAL_SECONDARY_INDEXES, "LocalSecondaryIndexes"),
      request(
          AttributeKeys.AWS_DYNAMODB_PROVISIONED_READ_CAPACITY,
          "ProvisionedThroughput.ReadCapacityUnits"),
      request(
          AttributeKeys.AWS_DYNAMODB_PROVISIONED_WRITE_CAPACITY,
          "ProvisionedThroughput.WriteCapacityUnits"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity"),
      response(AttributeKeys.AWS_DYNAMODB_ITEM_COLLECTION_METRICS, "ItemCollectionMetrics")),
  DeleteItem(
      DYNAMODB,
      "dynamodb.model.DeleteItemRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity"),
      response(AttributeKeys.AWS_DYNAMODB_ITEM_COLLECTION_METRICS, "ItemCollectionMetrics")),
  DeleteTable(
      DYNAMODB,
      "dynamodb.model.DeleteTableRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName")),
  DescribeTable(
      DYNAMODB,
      "dynamodb.model.DescribeTableRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName")),
  GetItem(
      DYNAMODB,
      "dynamodb.model.GetItemRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      request(AttributeKeys.AWS_DYNAMODB_PROJECTION, "ProjectionExpression"),
      request(AttributeKeys.AWS_DYNAMODB_CONSISTENT_READ, "ConsistentRead"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity")),
  ListTables(
      DYNAMODB,
      "dynamodb.model.ListTablesRequest",
      request(AttributeKeys.AWS_DYNAMODB_EXCLUSIVE_START_TABLE, "ExclusiveStartTableName"),
      response(AttributeKeys.AWS_DYNAMODB_TABLE_COUNT, "TableNames"),
      request(AttributeKeys.AWS_DYNAMODB_LIMIT, "Limit")),
  PutItem(
      DYNAMODB,
      "dynamodb.model.PutItemRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity"),
      response(AttributeKeys.AWS_DYNAMODB_ITEM_COLLECTION_METRICS, "ItemCollectionMetrics")),
  Query(
      DYNAMODB,
      "dynamodb.model.QueryRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      request(AttributeKeys.AWS_DYNAMODB_ATTRIBUTES_TO_GET, "AttributesToGet"),
      request(AttributeKeys.AWS_DYNAMODB_CONSISTENT_READ, "ConsistentRead"),
      request(AttributeKeys.AWS_DYNAMODB_INDEX_NAME, "IndexName"),
      request(AttributeKeys.AWS_DYNAMODB_LIMIT, "Limit"),
      request(AttributeKeys.AWS_DYNAMODB_PROJECTION, "ProjectionExpression"),
      request(AttributeKeys.AWS_DYNAMODB_SCAN_FORWARD, "ScanIndexForward"),
      request(AttributeKeys.AWS_DYNAMODB_SELECT, "Select"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity")),
  Scan(
      DYNAMODB,
      "dynamodb.model.ScanRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      request(AttributeKeys.AWS_DYNAMODB_ATTRIBUTES_TO_GET, "AttributesToGet"),
      request(AttributeKeys.AWS_DYNAMODB_CONSISTENT_READ, "ConsistentRead"),
      request(AttributeKeys.AWS_DYNAMODB_INDEX_NAME, "IndexName"),
      request(AttributeKeys.AWS_DYNAMODB_LIMIT, "Limit"),
      request(AttributeKeys.AWS_DYNAMODB_PROJECTION, "ProjectionExpression"),
      request(AttributeKeys.AWS_DYNAMODB_SEGMENT, "Segment"),
      request(AttributeKeys.AWS_DYNAMODB_SELECT, "Select"),
      request(AttributeKeys.AWS_DYNAMODB_TOTAL_SEGMENTS, "TotalSegments"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity"),
      response(AttributeKeys.AWS_DYNAMODB_COUNT, "Count"),
      response(AttributeKeys.AWS_DYNAMODB_SCANNED_COUNT, "ScannedCount")),
  UpdateItem(
      DYNAMODB,
      "dynamodb.model.UpdateItemRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity"),
      response(AttributeKeys.AWS_DYNAMODB_ITEM_COLLECTION_METRICS, "ItemCollectionMetrics")),
  UpdateTable(
      DYNAMODB,
      "dynamodb.model.UpdateTableRequest",
      request(AttributeKeys.AWS_DYNAMODB_TABLE_NAMES, "TableName"),
      request(AttributeKeys.AWS_DYNAMODB_ATTRIBUTE_DEFINITIONS, "AttributeDefinitions"),
      request(
          AttributeKeys.AWS_DYNAMODB_GLOBAL_SECONDARY_INDEX_UPDATES, "GlobalSecondaryIndexUpdates"),
      request(
          AttributeKeys.AWS_DYNAMODB_PROVISIONED_READ_CAPACITY,
          "ProvisionedThroughput.ReadCapacityUnits"),
      request(
          AttributeKeys.AWS_DYNAMODB_PROVISIONED_WRITE_CAPACITY,
          "ProvisionedThroughput.WriteCapacityUnits"),
      response(AttributeKeys.AWS_DYNAMODB_CONSUMED_CAPACITY, "ConsumedCapacity")),
  ConverseRequest(
      BEDROCK_RUNTIME,
      "bedrockruntime.model.ConverseRequest",
      request(AttributeKeys.GEN_AI_REQUEST_MODEL, "modelId")),
  ExecuteStatementRequest(RDS_DATA, "rdsdata.model.ExecuteStatementRequest"),
  BatchExecuteStatementRequest(RDS_DATA, "rdsdata.model.BatchExecuteStatementRequest");

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

  private static class AttributeKeys {
    // copied from AwsIncubatingAttributes
    private static final AttributeKey<List<String>> AWS_DYNAMODB_ATTRIBUTES_TO_GET =
        stringArrayKey("aws.dynamodb.attributes_to_get");
    private static final AttributeKey<List<String>> AWS_DYNAMODB_ATTRIBUTE_DEFINITIONS =
        stringArrayKey("aws.dynamodb.attribute_definitions");
    private static final AttributeKey<Boolean> AWS_DYNAMODB_CONSISTENT_READ =
        booleanKey("aws.dynamodb.consistent_read");
    private static final AttributeKey<List<String>> AWS_DYNAMODB_CONSUMED_CAPACITY =
        stringArrayKey("aws.dynamodb.consumed_capacity");
    private static final AttributeKey<Long> AWS_DYNAMODB_COUNT = longKey("aws.dynamodb.count");
    private static final AttributeKey<String> AWS_DYNAMODB_EXCLUSIVE_START_TABLE =
        stringKey("aws.dynamodb.exclusive_start_table");
    private static final AttributeKey<List<String>> AWS_DYNAMODB_GLOBAL_SECONDARY_INDEXES =
        stringArrayKey("aws.dynamodb.global_secondary_indexes");
    private static final AttributeKey<List<String>> AWS_DYNAMODB_GLOBAL_SECONDARY_INDEX_UPDATES =
        stringArrayKey("aws.dynamodb.global_secondary_index_updates");
    private static final AttributeKey<String> AWS_DYNAMODB_INDEX_NAME =
        stringKey("aws.dynamodb.index_name");
    private static final AttributeKey<String> AWS_DYNAMODB_ITEM_COLLECTION_METRICS =
        stringKey("aws.dynamodb.item_collection_metrics");
    private static final AttributeKey<Long> AWS_DYNAMODB_LIMIT = longKey("aws.dynamodb.limit");
    private static final AttributeKey<List<String>> AWS_DYNAMODB_LOCAL_SECONDARY_INDEXES =
        stringArrayKey("aws.dynamodb.local_secondary_indexes");
    private static final AttributeKey<String> AWS_DYNAMODB_PROJECTION =
        stringKey("aws.dynamodb.projection");
    private static final AttributeKey<Double> AWS_DYNAMODB_PROVISIONED_READ_CAPACITY =
        doubleKey("aws.dynamodb.provisioned_read_capacity");
    private static final AttributeKey<Double> AWS_DYNAMODB_PROVISIONED_WRITE_CAPACITY =
        doubleKey("aws.dynamodb.provisioned_write_capacity");
    private static final AttributeKey<Long> AWS_DYNAMODB_SCANNED_COUNT =
        longKey("aws.dynamodb.scanned_count");
    private static final AttributeKey<Boolean> AWS_DYNAMODB_SCAN_FORWARD =
        booleanKey("aws.dynamodb.scan_forward");
    private static final AttributeKey<Long> AWS_DYNAMODB_SEGMENT = longKey("aws.dynamodb.segment");
    private static final AttributeKey<String> AWS_DYNAMODB_SELECT =
        stringKey("aws.dynamodb.select");
    private static final AttributeKey<Long> AWS_DYNAMODB_TABLE_COUNT =
        longKey("aws.dynamodb.table_count");
    private static final AttributeKey<List<String>> AWS_DYNAMODB_TABLE_NAMES =
        stringArrayKey("aws.dynamodb.table_names");
    private static final AttributeKey<Long> AWS_DYNAMODB_TOTAL_SEGMENTS =
        longKey("aws.dynamodb.total_segments");

    // copied from GenAiIncubatingAttributes
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL =
        stringKey("gen_ai.request.model");
  }
}
