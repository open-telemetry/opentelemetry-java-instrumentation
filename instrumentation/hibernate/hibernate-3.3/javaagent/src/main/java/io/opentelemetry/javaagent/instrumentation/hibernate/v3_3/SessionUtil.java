/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
import javax.annotation.Nullable;
import org.hibernate.Session;
import org.hibernate.StatelessSession;

public final class SessionUtil {

  private static final VirtualField<Session, SessionInfo> SESSION_SESSION_INFO =
      VirtualField.find(Session.class, SessionInfo.class);
  private static final VirtualField<StatelessSession, SessionInfo> STATELESS_SESSION_SESSION_INFO =
      VirtualField.find(StatelessSession.class, SessionInfo.class);

  /**
   * Gets the SessionInfo associated with the given session
   *
   * @param session session object
   * @return session info, or null if the session is not of expected type or has no session info
   *     associated with it
   */
  @Nullable
  public static SessionInfo getSessionInfo(Object session) {
    if (session instanceof Session) {
      return SESSION_SESSION_INFO.get((Session) session);
    } else if (session instanceof StatelessSession) {
      return STATELESS_SESSION_SESSION_INFO.get((StatelessSession) session);
    }
    return null;
  }

  /**
   * Set the SessionInfo associated with the given session.
   *
   * @param session session object
   */
  public static void setSessionInfo(Object session) {
    if (session instanceof Session) {
      SESSION_SESSION_INFO.set((Session) session, new SessionInfo());
    } else if (session instanceof StatelessSession) {
      STATELESS_SESSION_SESSION_INFO.set((StatelessSession) session, new SessionInfo());
    }
  }

  private SessionUtil() {}
}
