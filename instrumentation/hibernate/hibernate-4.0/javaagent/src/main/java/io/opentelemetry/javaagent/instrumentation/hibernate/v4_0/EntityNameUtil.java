/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import java.util.function.Function;
import org.hibernate.SharedSessionContract;
import org.hibernate.internal.SessionImpl;
import org.hibernate.internal.StatelessSessionImpl;

public final class EntityNameUtil {

  private EntityNameUtil() {}

  private static String bestGuessEntityName(SharedSessionContract session, Object entity) {
    if (entity == null) {
      return null;
    }

    if (session instanceof SessionImpl) {
      return ((SessionImpl) session).bestGuessEntityName(entity);
    } else if (session instanceof StatelessSessionImpl) {
      return ((StatelessSessionImpl) session).bestGuessEntityName(entity);
    }

    return null;
  }

  public static Function<Object, String> bestGuessEntityName(SharedSessionContract session) {
    return (entity) -> bestGuessEntityName(session, entity);
  }
}
