/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import java.util.logging.Logger;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlFeatures;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;

class JexlEvaluator {
  private static final Logger logger = Logger.getLogger(JexlEvaluator.class.getName());

  private final JexlEngine jexl;

  JexlEvaluator() {
    JexlFeatures features =
        new JexlFeatures()
            .register(false) // don't support #register syntax
            .comparatorNames(false); // don't support 'lt' as an alternative to '<'
    this.jexl =
        new JexlBuilder()
            .features(features)
            // "unrestricted" means "can introspect on custom classes"
            .permissions(JexlPermissions.UNRESTRICTED)
            // don't support ant syntax
            .antish(false)
            // This api is terribly named but false means "null deref throws exception rather than
            // log warning"
            .safe(false)
            // We will catch our own exceptions
            .silent(false)
            // Don't assume unknown methods/variables mean "null"
            .strict(true)
            .create();
  }

  private static void setBeginningVariables(JexlContext context, Object thiz, Object[] params) {
    context.set("this", thiz);
    for (int i = 0; i < params.length; i++) {
      context.set("param" + i, params[i]);
    }
  }

  private static Object evaluateExpression(JexlExpression expression, JexlContext context) {
    try {
      return expression.evaluate(context);
    } catch (Throwable t) {
      logger.warning("Can't evaluate {" + expression + "}: " + t);
      return null;
    }
  }

  JexlExpression createExpression(String expression) {
    try {
      return jexl.createExpression(expression);
    } catch (Throwable t) {
      logger.warning("Invalid expression {" + expression + "}: " + t);
      return null;
    }
  }

  private static void setEndingVariables(JexlContext context, Object returnValue, Throwable error) {
    context.set("returnValue", returnValue);
    context.set("error", error);
  }

  Object evaluate(JexlExpression expression, Object thiz, Object[] params) {
    JexlContext context = new MapContext();
    setBeginningVariables(context, thiz, params);
    return evaluateExpression(expression, context);
  }

  Object evaluateAtEnd(
      JexlExpression expression,
      Object thiz,
      Object[] params,
      Object returnValue,
      Throwable error) {
    JexlContext context = new MapContext();
    setBeginningVariables(context, thiz, params);
    setEndingVariables(context, returnValue, error);
    return evaluateExpression(expression, context);
  }
}
