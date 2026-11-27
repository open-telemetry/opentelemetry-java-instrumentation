/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import static java.util.logging.Level.FINE;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class JettyContentLength {
  private static final Logger logger = Logger.getLogger(JettyContentLength.class.getName());
  private static final String JETTY_RESPONSE_CLASS = "org.eclipse.jetty.server.Response";
  private static final String JETTY_EE9_RESPONSE_CLASS = "org.eclipse.jetty.ee9.nested.Response";
  private static final String JETTY_EE10_RESPONSE_CLASS =
      "org.eclipse.jetty.ee10.servlet.ServletApiResponse";
  private static final String INCLUDE_HEADER_PREFIX = "org.eclipse.jetty.server.include.";

  private static final ClassValue<ContentLengthAccessor> ACCESSORS =
      new ClassValue<ContentLengthAccessor>() {
        @Override
        protected ContentLengthAccessor computeValue(Class<?> responseClass) {
          return new ContentLengthAccessor(
              findVirtual(responseClass, "isCommitted"), findVirtual(responseClass, "isIncluding"));
        }
      };

  public enum Setter {
    CONTENT_LENGTH,
    SET_HEADER,
    ADD_HEADER,
    SET_INT_HEADER,
    ADD_INT_HEADER
  }

  public static boolean isContentLengthUpdateAllowed(
      Object response, Setter setter, boolean includePrefixedHeader) {
    String responseClassName = response.getClass().getName();
    if (!isJettyResponse(responseClassName)) {
      return false;
    }
    return ACCESSORS
        .get(response.getClass())
        .isUpdateAllowed(
            response,
            JETTY_EE10_RESPONSE_CLASS.equals(responseClassName),
            setter,
            includePrefixedHeader);
  }

  public static void recordAcceptedContentLength(
      Object response, InjectionState state, String methodName, Object[] arguments) {
    Setter setter;
    String headerName = null;
    long length;
    if ("setContentLength".equals(methodName) || "setContentLengthLong".equals(methodName)) {
      setter = Setter.CONTENT_LENGTH;
      if (arguments.length != 1 || !(arguments[0] instanceof Number)) {
        return;
      }
      length = ((Number) arguments[0]).longValue();
    } else {
      if (arguments.length != 2 || !(arguments[0] instanceof String)) {
        return;
      }
      headerName = (String) arguments[0];
      if ("setHeader".equals(methodName)) {
        setter = Setter.SET_HEADER;
        if (!(arguments[1] instanceof String)) {
          return;
        }
        length = parseContentLength(headerName, (String) arguments[1]);
      } else if ("addHeader".equals(methodName)) {
        setter = Setter.ADD_HEADER;
        if (!(arguments[1] instanceof String)) {
          return;
        }
        length = parseContentLength(headerName, (String) arguments[1]);
      } else if ("setIntHeader".equals(methodName)) {
        setter = Setter.SET_INT_HEADER;
        if (!(arguments[1] instanceof Number)) {
          return;
        }
        length = ((Number) arguments[1]).longValue();
      } else if ("addIntHeader".equals(methodName)) {
        setter = Setter.ADD_INT_HEADER;
        if (!(arguments[1] instanceof Number)) {
          return;
        }
        length = ((Number) arguments[1]).longValue();
      } else {
        return;
      }
      if (!isContentLengthHeader(headerName)) {
        return;
      }
    }

    boolean includePrefixedHeader =
        headerName != null
            && headerName.startsWith(INCLUDE_HEADER_PREFIX)
            && isContentLengthHeader(headerName.substring(INCLUDE_HEADER_PREFIX.length()));
    if (length > 0
        && state.isContentLengthAdjusted()
        && state.getContentLength() == length
        && isContentLengthUpdateAllowed(response, setter, includePrefixedHeader)) {
      state.setContentLength(length);
    }
  }

  private static boolean isJettyResponse(String responseClassName) {
    return JETTY_RESPONSE_CLASS.equals(responseClassName)
        || JETTY_EE9_RESPONSE_CLASS.equals(responseClassName)
        || JETTY_EE10_RESPONSE_CLASS.equals(responseClassName);
  }

  private static boolean isContentLengthHeader(String name) {
    return "Content-Length".equalsIgnoreCase(name)
        || (name.startsWith(INCLUDE_HEADER_PREFIX)
            && "Content-Length".equalsIgnoreCase(name.substring(INCLUDE_HEADER_PREFIX.length())));
  }

  private static long parseContentLength(String name, String value) {
    if (name.startsWith(INCLUDE_HEADER_PREFIX)) {
      name = name.substring(INCLUDE_HEADER_PREFIX.length());
    }
    if (!"Content-Length".equalsIgnoreCase(name)) {
      return Long.MIN_VALUE;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return Long.MIN_VALUE;
    }
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

    boolean isUpdateAllowed(
        Object response, boolean ee10Response, Setter setter, boolean includePrefixedHeader) {
      if (isCommitted == null) {
        return false;
      }
      try {
        if ((boolean) isCommitted.invoke(response)) {
          return false;
        }
        if (ee10Response) {
          return !includePrefixedHeader;
        }
        if (isIncluding == null) {
          return false;
        }
        boolean including = (boolean) isIncluding.invoke(response);
        return including
            ? setter == Setter.SET_HEADER && includePrefixedHeader
            : !includePrefixedHeader;
      } catch (Throwable t) {
        logger.log(FINE, "Failed to check whether Jetty's Content-Length can be updated", t);
        return false;
      }
    }
  }

  private JettyContentLength() {}
}
