/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.DynamoDB;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.Kinesis;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.S3;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.SQS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.request;
import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.response;

import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;

/**
 * Temporary solution - maps only DynamoDB attributes. Final solution should be generated from AWS
 * SDK automatically
 * (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2291).
 */
enum AwsSdkRequest {
  // generic requests
  DynamoDbRequest(DynamoDB, "DynamoDbRequest"),
  S3Request(S3, "S3Request"),
  SqsRequest(SQS, "SqsRequest"),
  KinesisRequest(Kinesis, "KinesisRequest"),
  // specific requests
  BatchGetItem(
      DynamoDB,
      "BatchGetItemRequest",
      request("aws.dynamodb.table_names", "requestItems"),
      response("aws.dynamodb.consumed_capacity", "consumedCapacity")),
  BatchWriteItem(
      DynamoDB,
      "BatchWriteItemRequest",
      request("aws.dynamodb.table_names", "requestItems"),
      response("aws.dynamodb.consumed_capacity", "consumedCapacity"),
      response("aws.dynamodb.item_collection_metrics", "itemCollectionMetrics")),
  CreateTable(
      DynamoDB,
      "CreateTableRequest",
      request("aws.dynamodb.global_secondary_indexes", "globalSecondaryIndexes"),
      request("aws.dynamodb.local_secondary_indexes", "localSecondaryIndexes"),
      request(
          "aws.dynamodb.provisioned_throughput.read_capacity_units",
          "provisionedThroughput.readCapacityUnits"),
      request(
          "aws.dynamodb.provisioned_throughput.write_capacity_units",
          "provisionedThroughput.writeCapacityUnits")),
  DeleteItem(
      DynamoDB,
      "DeleteItemRequest",
      response("aws.dynamodb.consumed_capacity", "consumedCapacity"),
      response("aws.dynamodb.item_collection_metrics", "itemCollectionMetrics")),
  GetItem(
      DynamoDB,
      "GetItemRequest",
      request("aws.dynamodb.projection_expression", "projectionExpression"),
      response("aws.dynamodb.consumed_capacity", "consumedCapacity"),
      request("aws.dynamodb.consistent_read", "consistentRead")),
  ListTables(
      DynamoDB,
      "ListTablesRequest",
      request("aws.dynamodb.exclusive_start_table_name", "exclusiveStartTableName"),
      response("aws.dynamodb.table_count", "tableNames"),
      request("aws.dynamodb.limit", "limit")),
  PutItem(
      DynamoDB,
      "PutItemRequest",
      response("aws.dynamodb.consumed_capacity", "consumedCapacity"),
      response("aws.dynamodb.item_collection_metrics", "itemCollectionMetrics")),
  Query(
      DynamoDB,
      "QueryRequest",
      request("aws.dynamodb.attributes_to_get", "attributesToGet"),
      request("aws.dynamodb.consistent_read", "consistentRead"),
      request("aws.dynamodb.index_name", "indexName"),
      request("aws.dynamodb.limit", "limit"),
      request("aws.dynamodb.projection_expression", "projectionExpression"),
      request("aws.dynamodb.scan_index_forward", "scanIndexForward"),
      request("aws.dynamodb.select", "select"),
      response("aws.dynamodb.consumed_capacity", "consumedCapacity")),
  Scan(
      DynamoDB,
      "ScanRequest",
      request("aws.dynamodb.attributes_to_get", "attributesToGet"),
      request("aws.dynamodb.consistent_read", "consistentRead"),
      request("aws.dynamodb.index_name", "indexName"),
      request("aws.dynamodb.limit", "limit"),
      request("aws.dynamodb.projection_expression", "projectionExpression"),
      request("aws.dynamodb.segment", "segment"),
      request("aws.dynamodb.select", "select"),
      request("aws.dynamodb.total_segments", "totalSegments"),
      response("aws.dynamodb.consumed_capacity", "consumedCapacity"),
      response("aws.dynamodb.count", "count"),
      response("aws.dynamodb.scanned_count", "scannedCount")),
  UpdateItem(
      DynamoDB,
      "UpdateItemRequest",
      response("aws.dynamodb.consumed_capacity", "consumedCapacity"),
      response("aws.dynamodb.item_collection_metrics", "itemCollectionMetrics")),
  UpdateTable(
      DynamoDB,
      "UpdateTableRequest",
      request("aws.dynamodb.attribute_definitions", "attributeDefinitions"),
      request("aws.dynamodb.global_secondary_index_updates", "globalSecondaryIndexUpdates"),
      request(
          "aws.dynamodb.provisioned_throughput.read_capacity_units",
          "provisionedThroughput.readCapacityUnits"),
      request(
          "aws.dynamodb.provisioned_throughput.write_capacity_units",
          "provisionedThroughput.writeCapacityUnits"));

  private final AwsSdkRequestType type;
  private final String requestClass;
  private final Map<FieldMapping.Type, List<FieldMapping>> fields;

  AwsSdkRequest(AwsSdkRequestType type, String requestClass, FieldMapping... fields) {
    this.type = type;
    this.requestClass = requestClass;
    this.fields = FieldMapping.map(fields);
  }

  @Nullable
  static AwsSdkRequest ofSdkRequest(SdkRequest request) {
    // try request type
    AwsSdkRequest result = ofType(request.getClass().getSimpleName());
    // try parent - generic
    if (result == null) {
      result = ofType(request.getClass().getSuperclass().getSimpleName());
    }
    return result;
  }

  private static AwsSdkRequest ofType(String typeName) {
    for (AwsSdkRequest type : values()) {
      if (type.requestClass.equals(typeName)) {
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
