/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.queryInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBImpl;
import retrofit2.Retrofit;

/**
 * Balances method-entry call-depth bookkeeping and restores any installed context at method exit.
 * Query state may outlive this scope until asynchronous callbacks finish. Even calls that start no
 * span need a placeholder to balance call depth.
 */
public class InfluxDbQueryAdviceScope {
  private static final ScopedThreadValue<QueryDelegation> currentQueryDelegation =
      new ScopedThreadValue<>();

  private final CallDepth callDepth;
  @Nullable private final Context parentContext;
  @Nullable private final InfluxDbQueryState queryState;
  @Nullable private final Scope scope;
  @Nullable private QueryDelegation previousDelegation;
  private boolean delegationInstalled;

  private InfluxDbQueryAdviceScope(
      CallDepth callDepth,
      @Nullable Context parentContext,
      @Nullable InfluxDbQueryState queryState,
      @Nullable Scope scope) {
    this.callDepth = callDepth;
    this.parentContext = parentContext;
    this.queryState = queryState;
    this.scope = scope;
  }

  public static InfluxDbQueryAdviceScope start(
      @Nullable Query query, Retrofit retrofit, boolean asynchronous, QueryMethod queryMethod) {
    CallDepth callDepth = CallDepth.forClass(InfluxDBImpl.class);
    int depth = callDepth.getAndIncrement();
    // Independent calls keep the tracing parent but must not inherit another query's completion
    // state, including when asynchronous work propagates it to a new thread.
    if (depth > 0) {
      Context current = Context.current();
      InfluxDbQueryState currentState = current.get(InfluxDbQueryState.KEY);
      QueryDelegation delegation = currentQueryDelegation.get();
      boolean delegated =
          delegation != null && delegation.tryConsume(query, retrofit, currentState, queryMethod);
      if (delegated) {
        InfluxDbQueryAdviceScope adviceScope =
            new InfluxDbQueryAdviceScope(callDepth, null, null, null);
        if (currentState != null) {
          adviceScope.installDelegation(query, retrofit, currentState, queryMethod);
        }
        return adviceScope;
      }
      Scope scope =
          currentState != null ? current.with(InfluxDbQueryState.KEY, null).makeCurrent() : null;
      return new InfluxDbQueryAdviceScope(callDepth, null, null, scope);
    }
    Context current = Context.current();
    if (current.get(InfluxDbQueryState.KEY) != null) {
      Scope scope = current.with(InfluxDbQueryState.KEY, null).makeCurrent();
      return new InfluxDbQueryAdviceScope(callDepth, null, null, scope);
    }
    if (query == null) {
      return new InfluxDbQueryAdviceScope(callDepth, null, null, null);
    }

    Context parentContext = current;
    HttpUrl httpUrl = retrofit.baseUrl();
    InfluxDbQuery influxDbQuery =
        InfluxDbQuery.create(
            httpUrl.host(), httpUrl.port(), query.getDatabase(), query.getCommand());
    if (!queryInstrumenter().shouldStart(parentContext, influxDbQuery)) {
      return new InfluxDbQueryAdviceScope(callDepth, null, null, null);
    }

    InfluxDbQueryState queryState =
        new InfluxDbQueryState(parentContext, influxDbQuery, asynchronous);
    InfluxDbQueryAdviceScope adviceScope =
        new InfluxDbQueryAdviceScope(
            callDepth, parentContext, queryState, queryState.getContext().makeCurrent());
    adviceScope.installDelegation(query, retrofit, queryState, queryMethod);
    return adviceScope;
  }

  private void installDelegation(
      @Nullable Query query,
      Retrofit retrofit,
      InfluxDbQueryState queryState,
      QueryMethod queryMethod) {
    QueryMethod expectedMethod = queryMethod.expectedDelegationTarget();
    if (expectedMethod == null) {
      return;
    }
    previousDelegation =
        currentQueryDelegation.set(
            new QueryDelegation(query, retrofit, queryState, expectedMethod));
    delegationInstalled = true;
  }

  @Nullable
  public Context parentContext() {
    return parentContext;
  }

  @Nullable
  public InfluxDbQueryState queryState() {
    return queryState;
  }

  public void end(@Nullable Throwable throwable, @Nullable QueryResult result) {
    int depth = callDepth.decrementAndGet();
    if (scope != null) {
      scope.close();
    }
    if (delegationInstalled) {
      currentQueryDelegation.restore(previousDelegation);
    }
    // Successful async calls finish in callback advice; synchronous completion or an escaping
    // exception finishes here.
    if (depth == 0 && queryState != null && (!queryState.isAsynchronous() || throwable != null)) {
      queryState.observe(result);
      queryState.end(throwable);
    }
  }

  public enum QueryMethod {
    SYNCHRONOUS,
    ASYNCHRONOUS,
    CHUNKED_CONSUMER,
    CHUNKED_BI_CONSUMER,
    CHUNKED_CONSUMER_COMPLETION,
    CHUNKED_BI_CONSUMER_COMPLETION,
    CHUNKED_BI_CONSUMER_COMPLETION_ERROR;

    @Nullable
    private QueryMethod expectedDelegationTarget() {
      // This is an expected forwarding target; older supported clients may implement it directly.
      switch (this) {
        case CHUNKED_CONSUMER:
          return CHUNKED_CONSUMER_COMPLETION;
        case CHUNKED_BI_CONSUMER:
        case CHUNKED_CONSUMER_COMPLETION:
          return CHUNKED_BI_CONSUMER_COMPLETION;
        case CHUNKED_BI_CONSUMER_COMPLETION:
          return CHUNKED_BI_CONSUMER_COMPLETION_ERROR;
        default:
          return null;
      }
    }
  }

  private static final class QueryDelegation {
    @Nullable private final Query query;
    private final Retrofit retrofit;
    private final InfluxDbQueryState queryState;
    private final QueryMethod expectedMethod;
    private boolean consumed;

    private QueryDelegation(
        @Nullable Query query,
        Retrofit retrofit,
        InfluxDbQueryState queryState,
        QueryMethod expectedMethod) {
      this.query = query;
      this.retrofit = retrofit;
      this.queryState = queryState;
      this.expectedMethod = expectedMethod;
    }

    private boolean tryConsume(
        @Nullable Query query,
        Retrofit retrofit,
        @Nullable InfluxDbQueryState queryState,
        QueryMethod queryMethod) {
      if (consumed) {
        return false;
      }
      // Only the next nested entry may claim this delegation, even when it does not match.
      consumed = true;
      return matches(query, retrofit, queryState, queryMethod);
    }

    private boolean matches(
        @Nullable Query query,
        Retrofit retrofit,
        @Nullable InfluxDbQueryState queryState,
        QueryMethod queryMethod) {
      return this.query == query
          && this.retrofit == retrofit
          && this.queryState == queryState
          && expectedMethod == queryMethod;
    }
  }
}
