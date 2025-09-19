/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcSingletons.statementInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcData;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import javax.annotation.Nullable;

public class JdbcAdviceScope {
  private final CallDepth callDepth;
  private final DbRequest request;
  private final Context context;
  private final Scope scope;

  private JdbcAdviceScope(CallDepth callDepth, DbRequest request, Context context, Scope scope) {
    this.callDepth = callDepth;
    this.request = request;
    this.context = context;
    this.scope = scope;
  }

  public static JdbcAdviceScope startBatch(CallDepth callDepth, Statement statement) {
    return start(callDepth, null, statement, null);
  }

  public static JdbcAdviceScope startStatement(
      CallDepth callDepth, String sql, Statement statement) {
    return start(callDepth, sql, statement, null);
  }

  public static JdbcAdviceScope startPreparedStatement(
      CallDepth callDepth, PreparedStatement preparedStatement) {
    return start(callDepth, null, null, preparedStatement);
  }

  private static JdbcAdviceScope start(
      CallDepth callDepth,
      @Nullable String sql,
      @Nullable Statement statement,
      @Nullable PreparedStatement preparedStatement) {
    // Connection#getMetaData() may execute a Statement or PreparedStatement to retrieve DB info
    // this happens before the DB CLIENT span is started (and put in the current context), so this
    // instrumentation runs again and the shouldStartSpan() check always returns true - and so on
    // until we get a StackOverflowError
    // using CallDepth prevents this, because this check happens before Connection#getMetadata()
    // is called - the first recursive Statement call is just skipped and we do not create a span
    // for it
    if (callDepth.getAndIncrement() > 0) {
      return new JdbcAdviceScope(callDepth, null, null, null);
    }

    Context parentContext = currentContext();

    DbRequest request;
    if (sql != null) {
      request = DbRequest.create(statement, sql);
    } else if (preparedStatement != null) {
      Map<String, String> parameters = JdbcData.getParameters(preparedStatement);
      request = DbRequest.create(preparedStatement, parameters);
    } else {
      // batch
      request = createBatchRequest(statement);
    }

    if (request == null || !statementInstrumenter().shouldStart(parentContext, request)) {
      return new JdbcAdviceScope(callDepth, null, null, null);
    }

    Context context = statementInstrumenter().start(parentContext, request);
    return new JdbcAdviceScope(callDepth, request, context, context.makeCurrent());
  }

  private static DbRequest createBatchRequest(Statement statement) {
    if (statement instanceof PreparedStatement) {
      String sql = JdbcData.preparedStatement.get((PreparedStatement) statement);
      if (sql == null) {
        return null;
      }
      Long batchSize = JdbcData.getPreparedStatementBatchSize((PreparedStatement) statement);
      Map<String, String> parameters = JdbcData.getParameters((PreparedStatement) statement);
      return DbRequest.create(statement, sql, batchSize, parameters);
    } else {
      JdbcData.StatementBatchInfo batchInfo = JdbcData.getStatementBatchInfo(statement);
      if (batchInfo == null) {
        return DbRequest.create(statement, null);
      } else {
        return DbRequest.create(statement, batchInfo.getStatements(), batchInfo.getBatchSize());
      }
    }
  }

  public void end(@Nullable Throwable throwable) {
    if (callDepth.decrementAndGet() > 0) {
      return;
    }
    if (scope == null) {
      return;
    }
    scope.close();
    statementInstrumenter().end(context, request, null, throwable);
  }
}
