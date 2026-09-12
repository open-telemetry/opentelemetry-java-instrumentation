/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class QueryBaseInstrumentation implements TypeInstrumentation {

  // Referenced by fully-qualified string because the advice class lives in the
  // io.vertx.sqlclient.impl package (it needs same-package access to package-private
  // QueryExecutor / QueryBase#builder) and cannot be imported from this package.
  private static final String COPY_ADVICE_CLASS_NAME =
      "io.vertx.sqlclient.impl.VertxSqlClientQueryBaseHelper";

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.QueryBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(namedOneOf("mapping", "collecting"), COPY_ADVICE_CLASS_NAME);
  }
}
