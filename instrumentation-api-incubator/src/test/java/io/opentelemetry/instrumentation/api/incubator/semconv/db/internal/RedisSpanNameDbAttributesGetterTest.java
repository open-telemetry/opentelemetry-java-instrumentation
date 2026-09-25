/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisSpanNameDbAttributesGetterTest {

  private final Object request = new Object();

  @Mock DbClientAttributesGetter<Object, Void> delegate;

  private RedisSpanNameDbAttributesGetter<Object> getter;

  @BeforeEach
  void setUp() {
    getter = new RedisSpanNameDbAttributesGetter<>(delegate);
  }

  @SuppressWarnings("deprecation") // getDbName is used for old semconv span names
  @Test
  void omitsNamespace() {
    assertThat(getter.getDbNamespace(request)).isNull();
    assertThat(getter.getDbName(request)).isNull();
    assertThat(getter.getDbQueryText(request)).isNull();
    verifyNoInteractions(delegate);
  }

  @SuppressWarnings("deprecation") // getDbOperation is used for old semconv span names
  @Test
  void delegatesSpanNameAttributes() {
    when(delegate.getDbQuerySummary(request)).thenReturn("query summary");
    when(delegate.getDbOperationName(request)).thenReturn("operation name");
    when(delegate.getDbOperation(request)).thenReturn("operation");
    when(delegate.getDbSystemName(request)).thenReturn("redis");
    when(delegate.getDbCollectionName(request)).thenReturn("collection");
    when(delegate.getServerAddress(request)).thenReturn("localhost");
    when(delegate.getServerPort(request)).thenReturn(6379);

    assertThat(getter.getDbQuerySummary(request)).isEqualTo("query summary");
    assertThat(getter.getDbOperationName(request)).isEqualTo("operation name");
    assertThat(getter.getDbOperation(request)).isEqualTo("operation");
    assertThat(getter.getDbSystemName(request)).isEqualTo("redis");
    assertThat(getter.getDbCollectionName(request)).isEqualTo("collection");
    assertThat(getter.getServerAddress(request)).isEqualTo("localhost");
    assertThat(getter.getServerPort(request)).isEqualTo(6379);
  }
}
