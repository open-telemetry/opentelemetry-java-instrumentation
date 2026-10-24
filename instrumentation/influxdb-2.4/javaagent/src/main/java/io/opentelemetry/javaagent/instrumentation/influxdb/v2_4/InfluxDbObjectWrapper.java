/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.influxdb.dto.QueryResult;

public class InfluxDbObjectWrapper {

  @Nullable
  public static <T> Consumer<T> wrapConsumer(
      @Nullable Consumer<T> callback, Context parentContext, InfluxDbQueryState state) {
    if (callback == null) {
      return null;
    }
    return value -> {
      if (value instanceof QueryResult) {
        state.observe((QueryResult) value);
      } else if (value instanceof Throwable) {
        state.onFailure((Throwable) value);
      }
      try (Scope ignore = parentContext.makeCurrent()) {
        callback.accept(value);
      } catch (Throwable t) {
        state.onCallbackError(t);
        throw t;
      }
    };
  }

  @Nullable
  public static <T, U> BiConsumer<T, U> wrapBiConsumer(
      @Nullable BiConsumer<T, U> callback, Context parentContext, InfluxDbQueryState state) {
    if (callback == null) {
      return null;
    }
    return (value1, value2) -> {
      if (value2 instanceof QueryResult) {
        state.observe((QueryResult) value2);
      }
      try (Scope ignore = parentContext.makeCurrent()) {
        callback.accept(value1, value2);
      } catch (Throwable t) {
        state.onCallbackError(t);
        throw t;
      }
    };
  }

  @Nullable
  public static Runnable wrapRunnable(
      @Nullable Runnable callback, Context parentContext, InfluxDbQueryState state) {
    if (callback == null) {
      return null;
    }
    return () -> {
      try (Scope ignore = parentContext.makeCurrent()) {
        callback.run();
      } catch (Throwable t) {
        state.onCallbackError(t);
        throw t;
      }
    };
  }

  private InfluxDbObjectWrapper() {}
}
