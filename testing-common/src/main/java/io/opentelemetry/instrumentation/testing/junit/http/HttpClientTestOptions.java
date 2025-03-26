/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

@AutoValue
public abstract class HttpClientTestOptions {

  public static final Set<AttributeKey<?>> DEFAULT_HTTP_ATTRIBUTES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                  ServerAttributes.SERVER_ADDRESS,
                  ServerAttributes.SERVER_PORT,
                  UrlAttributes.URL_FULL,
                  HttpAttributes.HTTP_REQUEST_METHOD)));

  public static final BiFunction<URI, String, String> DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER =
      (uri, method) -> HttpConstants._OTHER.equals(method) ? "HTTP" : method;

  public static final int FOUND_STATUS_CODE = HttpStatus.FOUND.code();

  public abstract Function<URI, Set<AttributeKey<?>>> getHttpAttributes();

  @Nullable
  public abstract Integer getResponseCodeOnRedirectError();

  public abstract BiFunction<URI, Throwable, Throwable> getClientSpanErrorMapper();

  /**
   * The returned function should create either a single connection to the target uri or a http
   * client which is guaranteed to use the same connection for all requests.
   */
  public abstract BiFunction<String, Integer, SingleConnection> getSingleConnectionFactory();

  public abstract BiFunction<URI, String, String> getExpectedClientSpanNameMapper();

  abstract HttpClientInstrumentationType getInstrumentationType();

  public boolean isLowLevelInstrumentation() {
    return getInstrumentationType() == HttpClientInstrumentationType.LOW_LEVEL;
  }

  public abstract boolean getTestWithClientParent();

  public abstract boolean getTestRedirects();

  public abstract boolean getTestCircularRedirects();

  /** Returns the maximum number of redirects that http client follows before giving up. */
  public abstract int getMaxRedirects();

  public abstract boolean getTestReusedRequest();

  public abstract boolean getTestConnectionFailure();

  public abstract boolean getTestReadTimeout();

  public abstract boolean getTestRemoteConnection();

  public abstract boolean getTestHttps();

  public abstract boolean getTestCallback();

  public abstract boolean getTestCallbackWithParent();

  public abstract boolean getTestErrorWithCallback();

  public abstract boolean getTestNonStandardHttpMethod();

  public abstract boolean getTestCaptureHttpHeaders();

  public abstract boolean getHasSendRequest();

  public abstract Function<URI, String> getHttpProtocolVersion();

  @Nullable
  abstract SpanEndsAfterType getSpanEndsAfterType();

  public boolean isSpanEndsAfterHeaders() {
    return getSpanEndsAfterType() == SpanEndsAfterType.HEADERS;
  }

  public boolean isSpanEndsAfterBody() {
    return getSpanEndsAfterType() == SpanEndsAfterType.BODY;
  }

  static Builder builder() {
    return new AutoValue_HttpClientTestOptions.Builder().withDefaults();
  }

  @AutoValue.Builder
  public interface Builder {

    @CanIgnoreReturnValue
    default Builder withDefaults() {
      return setHttpAttributes(x -> DEFAULT_HTTP_ATTRIBUTES)
          .setResponseCodeOnRedirectError(FOUND_STATUS_CODE)
          .setClientSpanErrorMapper((uri, exception) -> exception)
          .setSingleConnectionFactory((host, port) -> null)
          .setExpectedClientSpanNameMapper(DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER)
          .setInstrumentationType(HttpClientInstrumentationType.HIGH_LEVEL)
          .setSpanEndsAfterType(SpanEndsAfterType.HEADERS)
          .setTestWithClientParent(true)
          .setTestRedirects(true)
          .setTestCircularRedirects(true)
          .setMaxRedirects(2)
          .setTestReusedRequest(true)
          .setTestConnectionFailure(true)
          .setTestReadTimeout(true)
          .setTestRemoteConnection(true)
          .setTestHttps(true)
          .setTestCallback(true)
          .setTestCallbackWithParent(true)
          .setTestErrorWithCallback(true)
          .setTestNonStandardHttpMethod(true)
          .setTestCaptureHttpHeaders(true)
          .setHasSendRequest(true)
          .setHttpProtocolVersion(uri -> "1.1");
    }

    Builder setTestCaptureHttpHeaders(boolean value);

    Builder setHttpAttributes(Function<URI, Set<AttributeKey<?>>> value);

    Builder setResponseCodeOnRedirectError(Integer value);

    Builder setClientSpanErrorMapper(BiFunction<URI, Throwable, Throwable> value);

    Builder setSingleConnectionFactory(BiFunction<String, Integer, SingleConnection> value);

    Builder setExpectedClientSpanNameMapper(BiFunction<URI, String, String> value);

    Builder setInstrumentationType(HttpClientInstrumentationType instrumentationType);

    Builder setSpanEndsAfterType(SpanEndsAfterType spanEndsAfterType);

    Builder setTestWithClientParent(boolean value);

    Builder setTestRedirects(boolean value);

    Builder setTestCircularRedirects(boolean value);

    Builder setMaxRedirects(int value);

    Builder setTestReusedRequest(boolean value);

    Builder setTestConnectionFailure(boolean value);

    Builder setTestReadTimeout(boolean value);

    Builder setTestRemoteConnection(boolean value);

    Builder setTestHttps(boolean value);

    Builder setTestCallback(boolean value);

    Builder setTestCallbackWithParent(boolean value);

    Builder setTestErrorWithCallback(boolean value);

    Builder setTestNonStandardHttpMethod(boolean value);

    Builder setHasSendRequest(boolean value);

    Builder setHttpProtocolVersion(Function<URI, String> value);

    @CanIgnoreReturnValue
    default Builder disableTestWithClientParent() {
      return setTestWithClientParent(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestRedirects() {
      return setTestRedirects(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestCircularRedirects() {
      return setTestCircularRedirects(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestReusedRequest() {
      return setTestReusedRequest(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestConnectionFailure() {
      return setTestConnectionFailure(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestReadTimeout() {
      return setTestReadTimeout(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestRemoteConnection() {
      return setTestRemoteConnection(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestHttps() {
      return setTestHttps(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestCallback() {
      return setTestCallback(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestCallbackWithParent() {
      return setTestCallbackWithParent(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestErrorWithCallback() {
      return setTestErrorWithCallback(false);
    }

    @CanIgnoreReturnValue
    default Builder disableTestNonStandardHttpMethod() {
      return setTestNonStandardHttpMethod(false);
    }

    @CanIgnoreReturnValue
    default Builder markAsLowLevelInstrumentation() {
      return setInstrumentationType(HttpClientInstrumentationType.LOW_LEVEL);
    }

    @CanIgnoreReturnValue
    default Builder disableTestSpanEndsAfter() {
      return setSpanEndsAfterType(null);
    }

    @CanIgnoreReturnValue
    default Builder spanEndsAfterBody() {
      return setSpanEndsAfterType(SpanEndsAfterType.BODY);
    }

    HttpClientTestOptions build();
  }

  enum HttpClientInstrumentationType {
    /**
     * Creates a span for each attempt to send an HTTP request over the wire, follows the HTTP
     * resend spec.
     */
    LOW_LEVEL,
    /** Creates a single span for the topmost HTTP client operation. */
    HIGH_LEVEL
  }

  enum SpanEndsAfterType {
    /** HTTP client span ends when headers are received. */
    HEADERS,
    /** HTTP client span ends when the body is received */
    BODY
  }
}
