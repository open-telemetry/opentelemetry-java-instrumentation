/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.session.Session;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CassandraRequestTest {

  @Mock private Session session;

  @Test
  void omitsUnavailableSingleQueryText() {
    CassandraRequest request = CassandraRequest.create(session, null, mock(Statement.class));

    assertThat(attributes(request).get(DB_QUERY_TEXT)).isNull();
  }

  @Test
  void batchKeepsAvailableQueryTextWhenAnEntryHasNoQuery() {
    BatchableStatement<?> missingQuery = mock(BatchableStatement.class);
    BatchStatement batchStatement = mock(BatchStatement.class);
    when(batchStatement.iterator())
        .thenReturn(
            asList(missingQuery, SimpleStatement.newInstance("SELECT * FROM users")).iterator());
    when(batchStatement.size()).thenReturn(2);
    CassandraRequest request = CassandraRequest.create(session, null, batchStatement);

    Attributes attributes = attributes(request);

    assertThat(attributes.get(DB_QUERY_TEXT)).isEqualTo("SELECT * FROM users");
    assertThat(attributes.get(DB_OPERATION_BATCH_SIZE)).isEqualTo(2L);
  }

  private static Attributes attributes(CassandraRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    SqlClientAttributesExtractor.create(new CassandraSqlAttributesGetter())
        .onStart(attributes, Context.root(), request);
    return attributes.build();
  }
}
