/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.nocode;

public interface NocodeExpression {

  Object evaluate(Object thiz, Object[] params);

  Object evaluateAtEnd(Object thiz, Object[] params, Object returnValue, Throwable error);
}
