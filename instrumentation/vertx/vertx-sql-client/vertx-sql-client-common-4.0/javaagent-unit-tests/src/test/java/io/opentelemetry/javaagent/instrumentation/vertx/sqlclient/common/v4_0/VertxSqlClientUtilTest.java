/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnectOptions;
import org.junit.jupiter.api.Test;

class VertxSqlClientUtilTest {

  private static final Instrumenter<VertxSqlClientRequest, Void> INSTRUMENTER =
      Instrumenter.<VertxSqlClientRequest, Void>builder(
              OpenTelemetry.noop(), "test", request -> request.getQueryText())
          .buildInstrumenter();

  @Test
  void endsSuccessfulRequestOnlyOnce() {
    Promise<Object> promise = Promise.promise();
    VertxSqlClientUtil.attachRequest(promise, request("success"), Context.root(), Context.root());

    Scope scope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null);

    assertThat(scope).isNotNull();
    scope.close();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null)).isNull();
  }

  @Test
  void endsFailedRequestOnlyOnce() {
    Promise<Object> promise = Promise.promise();
    VertxSqlClientUtil.attachRequest(promise, request("failure"), Context.root(), Context.root());
    IllegalStateException error = new IllegalStateException("failure");

    Scope scope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, error);

    assertThat(scope).isNotNull();
    scope.close();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null)).isNull();
  }

  @Test
  void keepsDistinctPromisesIndependent() {
    Promise<Object> firstPromise = Promise.promise();
    Promise<Object> secondPromise = Promise.promise();
    VertxSqlClientUtil.attachRequest(
        firstPromise, request("first"), Context.root(), Context.root());
    VertxSqlClientUtil.attachRequest(
        secondPromise, request("second"), Context.root(), Context.root());

    Scope firstScope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, firstPromise, null);
    Scope secondScope =
        VertxSqlClientUtil.endQuerySpan(
            INSTRUMENTER, secondPromise, new IllegalStateException("second"));

    assertThat(firstScope).isNotNull();
    assertThat(secondScope).isNotNull();
    firstScope.close();
    secondScope.close();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, firstPromise, null)).isNull();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, secondPromise, null)).isNull();
  }

  private static VertxSqlClientRequest request(String query) {
    return new VertxSqlClientRequest(
        query, VertxSqlClientInfo.create(new SqlConnectOptions(), "postgresql"), false, null);
  }
}
