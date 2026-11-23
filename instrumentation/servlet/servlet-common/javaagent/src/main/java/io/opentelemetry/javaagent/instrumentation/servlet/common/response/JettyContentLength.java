/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import static java.util.logging.Level.FINE;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class JettyContentLength {
  private static final Logger logger = Logger.getLogger(JettyContentLength.class.getName());
  private static final String JETTY_RESPONSE_CLASS = "org.eclipse.jetty.server.Response";

  private static final ClassValue<ContentLengthAccessor> ACCESSORS =
      new ClassValue<ContentLengthAccessor>() {
        @Override
        protected ContentLengthAccessor computeValue(Class<?> responseClass) {
          return new ContentLengthAccessor(
              findVirtual(responseClass, "isCommitted"), findVirtual(responseClass, "isIncluding"));
        }
      };

  public static boolean isContentLengthUpdateAllowed(
      Object response, boolean includePrefixedHeader) {
    if (!JETTY_RESPONSE_CLASS.equals(response.getClass().getName())) {
      return false;
    }
    return ACCESSORS.get(response.getClass()).isUpdateAllowed(response, includePrefixedHeader);
  }

  @Nullable
  private static MethodHandle findVirtual(Class<?> type, String name) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(type, name, MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      logger.log(FINE, "Failed to access Jetty's " + name, e);
      return null;
    }
  }

  private static class ContentLengthAccessor {
    @Nullable private final MethodHandle isCommitted;
    @Nullable private final MethodHandle isIncluding;

    ContentLengthAccessor(@Nullable MethodHandle isCommitted, @Nullable MethodHandle isIncluding) {
      this.isCommitted = isCommitted;
      this.isIncluding = isIncluding;
    }

    boolean isUpdateAllowed(Object response, boolean includePrefixedHeader) {
      if (isCommitted == null || isIncluding == null) {
        return false;
      }
      try {
        if ((boolean) isCommitted.invoke(response)) {
          return false;
        }
        return (boolean) isIncluding.invoke(response) == includePrefixedHeader;
      } catch (Throwable t) {
        logger.log(FINE, "Failed to check whether Jetty's Content-Length can be updated", t);
        return false;
      }
    }
  }

  private JettyContentLength() {}
}
