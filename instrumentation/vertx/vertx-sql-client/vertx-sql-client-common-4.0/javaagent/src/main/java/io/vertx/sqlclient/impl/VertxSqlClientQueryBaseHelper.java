/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.sqlclient.impl;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

/**
 * Bridge that lives in {@code io.vertx.sqlclient.impl} because it needs same-package access to the
 * package-private {@link QueryExecutor} class and the package-private {@link QueryBase#builder}
 * field.
 *
 * <p>This class is registered as the advice class for {@code QueryBase#mapping} / {@code
 * QueryBase#collecting} (see {@code QueryBaseInstrumentation} in the {@code vertx-sql-client-4.0}
 * and {@code vertx-sql-client-5.0} javaagent modules) so that muzzle's {@code
 * SamePackageAccessValidator} exempts it from the same-package-access-to-non-public-library-code
 * check. The class is injected into the target class loader (see {@code
 * VertxSqlClientInstrumentationModule#injectedClassNames}) so that the {@link VirtualField#find}
 * call in its static initializer resolves {@link QueryExecutor} at runtime.
 */
public final class VertxSqlClientQueryBaseHelper {

  private static final VirtualField<QueryExecutor<?, ?, ?>, Object> DATA =
      VirtualField.find(QueryExecutor.class, Object.class);

  public static void setData(Object queryExecutor, @Nullable Object data) {
    DATA.set((QueryExecutor<?, ?, ?>) queryExecutor, data);
  }

  @Nullable
  public static Object getData(Object queryExecutor) {
    return DATA.get((QueryExecutor<?, ?, ?>) queryExecutor);
  }

  @SuppressWarnings("unused")
  @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
  public static void copyOnExit(
      @Advice.This Object sourceQuery, @Advice.Return Object copiedQuery) {
    QueryExecutor<?, ?, ?> sourceExecutor = ((QueryBase<?, ?>) sourceQuery).builder;
    QueryExecutor<?, ?, ?> copiedExecutor = ((QueryBase<?, ?>) copiedQuery).builder;
    DATA.set(copiedExecutor, DATA.get(sourceExecutor));
  }

  private VertxSqlClientQueryBaseHelper() {}
}
