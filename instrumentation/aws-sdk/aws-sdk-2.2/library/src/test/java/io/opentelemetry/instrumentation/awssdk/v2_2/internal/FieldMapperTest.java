/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequest.BatchWriteItem;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkRequest.UpdateTable;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_CONSUMED_CAPACITY;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_ITEM_COLLECTION_METRICS;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_PROVISIONED_READ_CAPACITY;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_PROVISIONED_WRITE_CAPACITY;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_TABLE_NAMES;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
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
    verify(span).setAttribute(AWS_DYNAMODB_PROVISIONED_READ_CAPACITY, 55.0);
    verify(span).setAttribute(AWS_DYNAMODB_PROVISIONED_WRITE_CAPACITY, 77.0);
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
    when(serializer.serializeCollection(items.keySet()))
        .thenReturn(asList("firstTable", "secondTable"));

    Span span = mock(Span.class);
    // when
    underTest.mapToAttributes(sdkRequest, awsSdkRequest, span);
    // then
    verify(span).setAttribute(AWS_DYNAMODB_TABLE_NAMES, asList("firstTable", "secondTable"));
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
    when(serializer.serializeCollection(sdkResponse.consumedCapacity()))
        .thenReturn(singletonList("consumedCapacity"));
    when(serializer.serialize(items)).thenReturn("itemCollectionMetrics");

    Span span = mock(Span.class);
    // when
    underTest.mapToAttributes(sdkResponse, awsSdkRequest, span);
    // then
    verify(span).setAttribute(AWS_DYNAMODB_CONSUMED_CAPACITY, singletonList("consumedCapacity"));
    verify(span).setAttribute(AWS_DYNAMODB_ITEM_COLLECTION_METRICS, "itemCollectionMetrics");
    verifyNoMoreInteractions(span);
  }
}
