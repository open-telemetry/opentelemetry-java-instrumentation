/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import java.util.function.Function;
import org.hibernate.impl.AbstractSessionImpl;

public final class EntityNameUtil {

  private EntityNameUtil() {}

  private static String bestGuessEntityName(Object session, Object entity) {
    if (entity == null) {
      return null;
    }

    if (session instanceof AbstractSessionImpl) {
      return ((AbstractSessionImpl) session).bestGuessEntityName(entity);
    }

    return null;
  }

  public static Function<Object, String> bestGuessEntityName(Object session) {
    return (entity) -> bestGuessEntityName(session, entity);
  }
}
