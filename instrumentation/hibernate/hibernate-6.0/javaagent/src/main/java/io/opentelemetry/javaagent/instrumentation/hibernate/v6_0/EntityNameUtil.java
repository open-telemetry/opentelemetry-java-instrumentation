/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import java.util.function.Function;
import javax.annotation.Nullable;
import org.hibernate.SharedSessionContract;
import org.hibernate.internal.SessionImpl;
import org.hibernate.internal.StatelessSessionImpl;

public class EntityNameUtil {

  public static Function<Object, String> bestGuessEntityName(SharedSessionContract session) {
    return entity -> bestGuessEntityName(session, entity);
  }

  @Nullable
  private static String bestGuessEntityName(
      SharedSessionContract session, @Nullable Object entity) {
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

  private EntityNameUtil() {}
}
