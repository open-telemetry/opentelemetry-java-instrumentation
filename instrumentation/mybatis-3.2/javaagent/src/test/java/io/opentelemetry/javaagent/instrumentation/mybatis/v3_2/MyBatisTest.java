/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MyBatisTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static SqlSession sqlSession;

  @BeforeAll
  static void setUp() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
    Configuration configuration = new Configuration();
    configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
    configuration.addMapper(TestMapper.class);
    sqlSession = new SqlSessionFactoryBuilder().build(configuration).openSession();
  }

  @AfterAll
  static void cleanUp() {
    if (sqlSession != null) {
      sqlSession.close();
    }
  }

  @Test
  void testSelect() {
    TestMapper testMapper = sqlSession.getMapper(TestMapper.class);
    testMapper.select();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.INTERNAL)
                        .hasName("TestMapper.select")
                        .hasAttributesSatisfyingExactly(
                            codeFunctionAssertions(TestMapper.class, "select"))));
  }
}
