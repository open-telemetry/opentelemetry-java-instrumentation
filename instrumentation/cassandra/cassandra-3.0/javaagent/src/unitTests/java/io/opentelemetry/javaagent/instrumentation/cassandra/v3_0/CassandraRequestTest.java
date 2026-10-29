/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CassandraRequestTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Session session;

  @BeforeEach
  void setUp() {
    QueryOptions queryOptions = session.getCluster().getConfiguration().getQueryOptions();
    when(queryOptions.getConsistencyLevel()).thenReturn(ConsistencyLevel.LOCAL_ONE);
    when(queryOptions.getFetchSize()).thenReturn(5000);
  }

  @Test
  void omitsUnavailableSingleQueryText() {
    CassandraRequest request = CassandraRequest.create(session, mock(Statement.class), null);

    assertThat(attributes(request).get(DB_QUERY_TEXT)).isNull();
  }

  @Test
  void batchKeepsAvailableQueryTextWhenAnEntryHasNoQuery() {
    Statement missingQuery = mock(Statement.class);
    BatchStatement batchStatement = mock(BatchStatement.class);
    when(batchStatement.getStatements())
        .thenReturn(asList(missingQuery, new SimpleStatement("SELECT * FROM users")));
    when(batchStatement.size()).thenReturn(2);
    CassandraRequest request = CassandraRequest.create(session, batchStatement, null);

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
