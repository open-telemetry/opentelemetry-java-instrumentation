/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequest.BatchWriteItem;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequest.UpdateTable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ItemCollectionMetrics;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.UpdateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

class FieldMapperTest {

  @Test
  void shouldMapNestedField() {
    // given
    AwsSdkRequest awsSdkRequest = UpdateTable;
    MethodHandleFactory methodHandleFactory = new MethodHandleFactory();
    Serializer serializer = mock(Serializer.class);
    FieldMapper underTest = new FieldMapper(serializer, methodHandleFactory);
    UpdateTableRequest sdkRequest =
        UpdateTableRequest.builder()
            .provisionedThroughput(
                ProvisionedThroughput.builder()
                    .readCapacityUnits(55L)
                    .writeCapacityUnits(77L)
                    .build())
            .build();
    when(serializer.serialize(55L)).thenReturn("55");
    when(serializer.serialize(77L)).thenReturn("77");

    Span span = mock(Span.class);
    // when
    underTest.mapToAttributes(sdkRequest, awsSdkRequest, span);
    // then
    verify(span).setAttribute("aws.dynamodb.provisioned_throughput.read_capacity_units", "55");
    verify(span).setAttribute("aws.dynamodb.provisioned_throughput.write_capacity_units", "77");
    verifyNoMoreInteractions(span);
  }

  @Test
  void shouldMapRequestFieldsOnly() {
    // given
    AwsSdkRequest awsSdkRequest = BatchWriteItem;
    MethodHandleFactory methodHandleFactory = new MethodHandleFactory();
    Serializer serializer = mock(Serializer.class);
    FieldMapper underTest = new FieldMapper(serializer, methodHandleFactory);
    Map<String, Collection<WriteRequest>> items = new HashMap<>();
    BatchWriteItemRequest sdkRequest = BatchWriteItemRequest.builder().requestItems(items).build();
    when(serializer.serialize(items)).thenReturn("firstTable,secondTable");

    Span span = mock(Span.class);
    // when
    underTest.mapToAttributes(sdkRequest, awsSdkRequest, span);
    // then
    verify(span).setAttribute("aws.dynamodb.table_names", "firstTable,secondTable");
    verifyNoMoreInteractions(span);
  }

  @Test
  void shouldMapResponseFieldsOnly() {
    // given
    AwsSdkRequest awsSdkRequest = BatchWriteItem;
    MethodHandleFactory methodHandleFactory = new MethodHandleFactory();
    Serializer serializer = mock(Serializer.class);
    FieldMapper underTest = new FieldMapper(serializer, methodHandleFactory);
    Map<String, Collection<ItemCollectionMetrics>> items = new HashMap<>();
    BatchWriteItemResponse sdkResponse =
        BatchWriteItemResponse.builder()
            .consumedCapacity(ConsumedCapacity.builder().build())
            .itemCollectionMetrics(items)
            .build();
    when(serializer.serialize(sdkResponse.consumedCapacity())).thenReturn("consumedCapacity");
    when(serializer.serialize(items)).thenReturn("itemCollectionMetrics");

    Span span = mock(Span.class);
    // when
    underTest.mapToAttributes(sdkResponse, awsSdkRequest, span);
    // then
    verify(span).setAttribute("aws.dynamodb.consumed_capacity", "consumedCapacity");
    verify(span).setAttribute("aws.dynamodb.item_collection_metrics", "itemCollectionMetrics");
    verifyNoMoreInteractions(span);
  }
}
