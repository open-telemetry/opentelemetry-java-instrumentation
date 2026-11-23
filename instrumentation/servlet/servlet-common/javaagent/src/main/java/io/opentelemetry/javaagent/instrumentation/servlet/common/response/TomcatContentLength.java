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

public final class TomcatContentLength {
  private static final Logger logger = Logger.getLogger(TomcatContentLength.class.getName());

  private static final ClassValue<ContentLengthAccessor> CONTENT_LENGTH_ACCESSORS =
      new ClassValue<ContentLengthAccessor>() {
        @Override
        protected ContentLengthAccessor computeValue(Class<?> responseClass) {
          Class<?> coyoteResponseClass = null;
          try {
            coyoteResponseClass =
                Class.forName("org.apache.coyote.Response", false, responseClass.getClassLoader());
          } catch (ClassNotFoundException e) {
            logger.log(FINE, "Failed to access Tomcat's effective Content-Length", e);
          }
          MethodHandle getCoyoteResponse =
              coyoteResponseClass == null
                  ? null
                  : findVirtual(responseClass, "getCoyoteResponse", coyoteResponseClass);
          MethodHandle getContentLength =
              coyoteResponseClass == null
                  ? null
                  : findVirtual(coyoteResponseClass, "getContentLengthLong", long.class);
          MethodHandle setContentLengthLong =
              findVirtual(responseClass, "setContentLengthLong", void.class, long.class);
          MethodHandle setContentLength =
              findVirtual(responseClass, "setContentLength", void.class, int.class);
          MethodHandle isCommitted = findVirtual(responseClass, "isCommitted", boolean.class);
          return new ContentLengthAccessor(
              getCoyoteResponse,
              getContentLength,
              setContentLengthLong,
              setContentLength,
              isCommitted);
        }
      };

  @Nullable
  private static MethodHandle findVirtual(
      Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(type, name, MethodType.methodType(returnType, parameterTypes));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      logger.log(FINE, "Failed to access Tomcat's " + name, e);
      return null;
    }
  }

  public static void record(Object response, InjectionState state) {
    long contentLength = CONTENT_LENGTH_ACCESSORS.get(response.getClass()).get(response);
    if (contentLength == Long.MIN_VALUE) {
      return;
    }
    state.setContentLengthResponse(response);
    if (contentLength < 0) {
      if (state.hasContentLength()) {
        state.setContentLength(-1);
      }
    } else if (!state.hasContentLength() || state.getContentLength() != contentLength) {
      state.setContentLength(contentLength);
    }
  }

  public static long get(Object response) {
    return CONTENT_LENGTH_ACCESSORS.get(response.getClass()).get(response);
  }

  public static boolean set(Object response, long contentLength) {
    return CONTENT_LENGTH_ACCESSORS.get(response.getClass()).set(response, contentLength);
  }

  public static boolean isTomcatIncludeResponse(Object response) {
    return response.getClass().getName().equals("org.apache.catalina.core.ApplicationHttpResponse");
  }

  private static class ContentLengthAccessor {
    @Nullable private final MethodHandle getCoyoteResponse;
    @Nullable private final MethodHandle getContentLength;
    @Nullable private final MethodHandle setContentLengthLong;
    @Nullable private final MethodHandle setContentLength;
    @Nullable private final MethodHandle isCommitted;

    ContentLengthAccessor(
        @Nullable MethodHandle getCoyoteResponse,
        @Nullable MethodHandle getContentLength,
        @Nullable MethodHandle setContentLengthLong,
        @Nullable MethodHandle setContentLength,
        @Nullable MethodHandle isCommitted) {
      this.getCoyoteResponse = getCoyoteResponse;
      this.getContentLength = getContentLength;
      this.setContentLengthLong = setContentLengthLong;
      this.setContentLength = setContentLength;
      this.isCommitted = isCommitted;
    }

    long get(Object response) {
      if (getCoyoteResponse == null || getContentLength == null) {
        return Long.MIN_VALUE;
      }
      try {
        Object coyoteResponse = getCoyoteResponse.invoke(response);
        return (long) getContentLength.invoke(coyoteResponse);
      } catch (Throwable t) {
        logger.log(FINE, "Failed to read Tomcat's effective Content-Length", t);
        return Long.MIN_VALUE;
      }
    }

    boolean set(Object response, long contentLength) {
      if ((setContentLengthLong == null && setContentLength == null) || isCommitted == null) {
        return false;
      }
      try {
        if ((boolean) isCommitted.invoke(response)) {
          return false;
        }
        if (setContentLengthLong != null) {
          setContentLengthLong.invoke(response, contentLength);
        } else if (contentLength >= Integer.MIN_VALUE && contentLength <= Integer.MAX_VALUE) {
          setContentLength.invoke(response, (int) contentLength);
        } else {
          return false;
        }
        return get(response) == contentLength;
      } catch (Throwable t) {
        logger.log(FINE, "Failed to update Tomcat's effective Content-Length", t);
        return false;
      }
    }
  }

  private TomcatContentLength() {}
}
