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
          try {
            Class<?> coyoteResponseClass =
                Class.forName("org.apache.coyote.Response", false, responseClass.getClassLoader());
            MethodHandle getCoyoteResponse =
                MethodHandles.publicLookup()
                    .findVirtual(
                        responseClass,
                        "getCoyoteResponse",
                        MethodType.methodType(coyoteResponseClass));
            MethodHandle getContentLength =
                MethodHandles.publicLookup()
                    .findVirtual(
                        coyoteResponseClass,
                        "getContentLengthLong",
                        MethodType.methodType(long.class));
            return new ContentLengthAccessor(getCoyoteResponse, getContentLength);
          } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            logger.log(FINE, "Failed to access Tomcat's effective Content-Length", e);
            return new ContentLengthAccessor(null, null);
          }
        }
      };

  public static void record(Object response, InjectionState state) {
    long contentLength = CONTENT_LENGTH_ACCESSORS.get(response.getClass()).get(response);
    if (contentLength == Long.MIN_VALUE) {
      return;
    }
    if (contentLength < 0) {
      if (state.hasContentLength()) {
        state.setContentLength(-1);
      }
    } else if (!state.hasContentLength() || state.getContentLength() != contentLength) {
      state.setContentLength(contentLength);
    }
  }

  private static class ContentLengthAccessor {
    @Nullable private final MethodHandle getCoyoteResponse;
    @Nullable private final MethodHandle getContentLength;

    ContentLengthAccessor(
        @Nullable MethodHandle getCoyoteResponse, @Nullable MethodHandle getContentLength) {
      this.getCoyoteResponse = getCoyoteResponse;
      this.getContentLength = getContentLength;
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
  }

  private TomcatContentLength() {}
}
