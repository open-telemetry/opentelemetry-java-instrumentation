/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc


import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryCallableStatement
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryPreparedStatement
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryStatement
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.semconv.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createStatementInstrumenter

class OpenTelemetryConnectionTest extends InstrumentationSpecification implements LibraryTestTrait {

  def "verify create statement"() {
    setup:
    def instr = createStatementInstrumenter(openTelemetry)
    def dbInfo = getDbInfo()
    def connection = new OpenTelemetryConnection(new TestConnection(), dbInfo, instr)
    String query = "SELECT * FROM users"
    def statement = connection.createStatement()
    runWithSpan("parent") {
      assert statement.execute(query)
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SELECT my_name.users"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" dbInfo.system
            "$SemanticAttributes.DB_NAME" dbInfo.name
            "$SemanticAttributes.DB_USER" dbInfo.user
            "$SemanticAttributes.DB_CONNECTION_STRING" dbInfo.shortUrl
            "$SemanticAttributes.NET_PEER_NAME" dbInfo.host
            "$SemanticAttributes.NET_PEER_PORT" dbInfo.port
            "$SemanticAttributes.DB_STATEMENT" query
            "$SemanticAttributes.DB_OPERATION" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE" "users"
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()
  }

  def "verify create statement returns otel wrapper"() {
    when:
    def ot = OpenTelemetry.propagating(ContextPropagators.noop())
    def instr = createStatementInstrumenter(ot)
    def connection = new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instr)

    then:
    connection.createStatement().class == OpenTelemetryStatement
    connection.createStatement(0, 0).class == OpenTelemetryStatement
    connection.createStatement(0, 0, 0).class == OpenTelemetryStatement
    connection.createStatement().instrumenter == instr
  }

  def "verify prepare statement"() {
    setup:
    def instr = createStatementInstrumenter(openTelemetry)
    def dbInfo = getDbInfo()
    def connection = new OpenTelemetryConnection(new TestConnection(), dbInfo, instr)
    String query = "SELECT * FROM users"
    def statement = connection.prepareStatement(query)
    runWithSpan("parent") {
      assert statement.execute()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SELECT my_name.users"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" dbInfo.system
            "$SemanticAttributes.DB_NAME" dbInfo.name
            "$SemanticAttributes.DB_USER" dbInfo.user
            "$SemanticAttributes.DB_CONNECTION_STRING" dbInfo.shortUrl
            "$SemanticAttributes.NET_PEER_NAME" dbInfo.host
            "$SemanticAttributes.NET_PEER_PORT" dbInfo.port
            "$SemanticAttributes.DB_STATEMENT" query
            "$SemanticAttributes.DB_OPERATION" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE" "users"
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()
  }

  def "verify prepare statement returns otel wrapper"() {
    when:
    def ot = OpenTelemetry.propagating(ContextPropagators.noop())
    def instr = createStatementInstrumenter(ot)
    def connection = new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instr)
    String query = "SELECT * FROM users"

    then:
    connection.prepareStatement(query).class == OpenTelemetryPreparedStatement
    connection.prepareStatement(query, [0] as int[]).class == OpenTelemetryPreparedStatement
    connection.prepareStatement(query, ["id"] as String[]).class == OpenTelemetryPreparedStatement
    connection.prepareStatement(query, 0).class == OpenTelemetryPreparedStatement
    connection.prepareStatement(query, 0, 0).class == OpenTelemetryPreparedStatement
    connection.prepareStatement(query, 0, 0, 0).class == OpenTelemetryPreparedStatement
    connection.prepareStatement(query).instrumenter == instr
  }

  def "verify prepare call"() {
    setup:
    def instr = createStatementInstrumenter(openTelemetry)
    def dbInfo = getDbInfo()
    def connection = new OpenTelemetryConnection(new TestConnection(), dbInfo, instr)
    String query = "SELECT * FROM users"
    def statement = connection.prepareCall(query)
    runWithSpan("parent") {
      assert statement.execute()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SELECT my_name.users"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" dbInfo.system
            "$SemanticAttributes.DB_NAME" dbInfo.name
            "$SemanticAttributes.DB_USER" dbInfo.user
            "$SemanticAttributes.DB_CONNECTION_STRING" dbInfo.shortUrl
            "$SemanticAttributes.NET_PEER_NAME" dbInfo.host
            "$SemanticAttributes.NET_PEER_PORT" dbInfo.port
            "$SemanticAttributes.DB_STATEMENT" query
            "$SemanticAttributes.DB_OPERATION" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE" "users"
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()
  }

  def "verify prepare call returns otel wrapper"() {
    when:
    def ot = OpenTelemetry.propagating(ContextPropagators.noop())
    def instr = createStatementInstrumenter(ot)
    def connection = new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instr)
    String query = "SELECT * FROM users"

    then:
    connection.prepareCall(query).class == OpenTelemetryCallableStatement
    connection.prepareCall(query, 0, 0).class == OpenTelemetryCallableStatement
    connection.prepareCall(query, 0, 0, 0).class == OpenTelemetryCallableStatement
    connection.prepareCall(query).instrumenter == instr
  }

  private DbInfo getDbInfo() {
    DbInfo.builder()
      .system("my_system")
      .subtype("my_sub_type")
      .shortUrl("my_connection_string")
      .user("my_user")
      .name("my_name")
      .db("my_db")
      .host("my_host")
      .port(1234)
      .build()
  }

}
