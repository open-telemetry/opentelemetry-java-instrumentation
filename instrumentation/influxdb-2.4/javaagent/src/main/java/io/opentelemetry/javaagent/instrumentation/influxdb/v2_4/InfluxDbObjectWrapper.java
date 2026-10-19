/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.influxdb.dto.QueryResult;

public class InfluxDbObjectWrapper {

  @SuppressWarnings("unchecked") // we check types before casting
  public static Object wrap(Object object, Context parentContext, InfluxDbQueryState state) {
    if (object instanceof Consumer) {
      return (Consumer<Object>)
          o -> {
            if (o instanceof QueryResult) {
              state.observe((QueryResult) o);
            } else if (o instanceof Throwable) {
              state.onFailure((Throwable) o);
            }
            try (Scope ignore = parentContext.makeCurrent()) {
              ((Consumer<Object>) object).accept(o);
            } catch (Throwable t) {
              state.onCallbackError(t);
              throw t;
            }
          };
    } else if (object instanceof BiConsumer) {
      return (BiConsumer<Object, Object>)
          (o1, o2) -> {
            if (o2 instanceof QueryResult) {
              state.observe((QueryResult) o2);
            }
            try (Scope ignore = parentContext.makeCurrent()) {
              ((BiConsumer<Object, Object>) object).accept(o1, o2);
            } catch (Throwable t) {
              state.onCallbackError(t);
              throw t;
            }
          };
    } else if (object instanceof Runnable) {
      return (Runnable)
          () -> {
            try (Scope ignore = parentContext.makeCurrent()) {
              ((Runnable) object).run();
            } catch (Throwable t) {
              state.onCallbackError(t);
              throw t;
            }
          };
    }

    return object;
  }

  private InfluxDbObjectWrapper() {}
}
