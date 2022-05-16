/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
import org.hibernate.Session;
import org.hibernate.StatelessSession;

public final class SessionUtil {
  private static final VirtualField<Session, SessionInfo> sessionVirtualField =
      VirtualField.find(Session.class, SessionInfo.class);
  private static final VirtualField<StatelessSession, SessionInfo> statelessSessionVirtualField =
      VirtualField.find(StatelessSession.class, SessionInfo.class);

  private SessionUtil() {}

  public static SessionInfo getSessionInfo(Object session) {
    if (session instanceof Session) {
      return sessionVirtualField.get((Session) session);
    } else if (session instanceof StatelessSession) {
      return statelessSessionVirtualField.get((StatelessSession) session);
    }
    return null;
  }
}
