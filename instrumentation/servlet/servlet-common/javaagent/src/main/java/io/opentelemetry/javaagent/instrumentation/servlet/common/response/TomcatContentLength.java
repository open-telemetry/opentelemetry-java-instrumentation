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

  private static final ContentLengthAccessor NO_ACCESSOR =
      new ContentLengthAccessor(null, null, null, null, null);

  private static final ClassValue<ContentLengthAccessor> ACCESSORS_BY_CATALINA_RESPONSE =
      new ClassValue<ContentLengthAccessor>() {
        @Override
        protected ContentLengthAccessor computeValue(Class<?> tomcatResponseClass) {
          Class<?> coyoteResponseClass = null;
          try {
            coyoteResponseClass =
                Class.forName(
                    "org.apache.coyote.Response", false, tomcatResponseClass.getClassLoader());
          } catch (ClassNotFoundException e) {
            logger.log(FINE, "Failed to access Tomcat's effective Content-Length", e);
          }
          MethodHandle getCoyoteResponse =
              coyoteResponseClass == null
                  ? null
                  : findVirtual(tomcatResponseClass, "getCoyoteResponse", coyoteResponseClass);
          MethodHandle getContentLength =
              coyoteResponseClass == null
                  ? null
                  : findVirtual(coyoteResponseClass, "getContentLengthLong", long.class);
          MethodHandle setContentLengthLong =
              findVirtual(tomcatResponseClass, "setContentLengthLong", void.class, long.class);
          MethodHandle setContentLength =
              setContentLengthLong == null
                  ? findVirtual(tomcatResponseClass, "setContentLength", void.class, int.class)
                  : null;
          MethodHandle isCommitted = findVirtual(tomcatResponseClass, "isCommitted", boolean.class);
          return new ContentLengthAccessor(
              getCoyoteResponse,
              getContentLength,
              setContentLengthLong,
              setContentLength,
              isCommitted);
        }
      };

  private static final ClassValue<ContentLengthAccessor> CONTENT_LENGTH_ACCESSORS =
      new ClassValue<ContentLengthAccessor>() {
        @Override
        protected ContentLengthAccessor computeValue(Class<?> responseClass) {
          Class<?> tomcatResponseClass = findTomcatResponseClass(responseClass);
          return tomcatResponseClass == null
              ? NO_ACCESSOR
              : ACCESSORS_BY_CATALINA_RESPONSE.get(tomcatResponseClass);
        }
      };

  @Nullable
  private static Class<?> findTomcatResponseClass(Class<?> responseClass) {
    for (Class<?> type = responseClass; type != null; type = type.getSuperclass()) {
      if (type.getName().equals("org.apache.catalina.connector.Response")) {
        return type;
      }
    }
    return null;
  }

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

  public static void recordAccepted(Object response, InjectionState state, long declaredLength) {
    long contentLength =
        CONTENT_LENGTH_ACCESSORS.get(response.getClass()).getAccepted(response, declaredLength);
    if (contentLength != Long.MIN_VALUE) {
      state.setContentLengthResponse(response);
      state.setContentLength(contentLength);
    }
  }

  public static void recordAccepted(
      Object response, InjectionState state, @Nullable String declaredLength) {
    if (declaredLength == null) {
      return;
    }
    try {
      recordAccepted(response, state, Long.parseLong(declaredLength));
    } catch (NumberFormatException ignored) {
      // Tomcat does not accept a non-numeric Content-Length declaration.
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

    long getAccepted(Object response, long declaredLength) {
      if (isCommitted == null) {
        return Long.MIN_VALUE;
      }
      try {
        if ((boolean) isCommitted.invoke(response)) {
          return Long.MIN_VALUE;
        }
      } catch (Throwable t) {
        logger.log(FINE, "Failed to check whether Tomcat's response is committed", t);
        return Long.MIN_VALUE;
      }
      long contentLength = get(response);
      long effectiveDeclaredLength = declaredLength < 0 ? -1 : declaredLength;
      return contentLength == effectiveDeclaredLength ? contentLength : Long.MIN_VALUE;
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
