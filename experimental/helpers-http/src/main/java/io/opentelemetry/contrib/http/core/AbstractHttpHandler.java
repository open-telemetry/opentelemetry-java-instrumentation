/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.contrib.http.core;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static io.opentelemetry.contrib.http.core.HttpTraceConstants.INSTRUMENTATION_LIB_ID;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.correlationcontext.CorrelationContext;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Event;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Base class for handling request on http client and server.
 *
 * @param <Q> the HTTP request entity.
 * @param <P> the HTTP response entity.
 */
abstract class AbstractHttpHandler<Q, P> {

  /** The {@link HttpExtractor} used to extract information from request/response. */
  @VisibleForTesting final HttpExtractor<Q, P> extractor;

  @VisibleForTesting final StatusCodeConverter statusConverter;
  private final Meter meter;

  /**
   * Constructor to allow access from subclasses in the same package only. Uses the defaults for
   * status converter and meter.
   *
   * @param extractor the implementation of HTTP extractor which handles the particular classes used
   *     to hold HTTP request and response info in the library being instrumented.
   */
  AbstractHttpHandler(HttpExtractor<Q, P> extractor) {
    checkNotNull(extractor, "extractor is required");
    this.extractor = extractor;
    this.statusConverter = new StatusCodeConverter();
    this.meter = OpenTelemetry.getMeterRegistry().get(INSTRUMENTATION_LIB_ID);
  }

  /**
   * Constructor to allow access from subclasses in the same package only.
   *
   * @param extractor the implementation of HTTP extractor which handles the particular classes used
   *     to hold HTTP request and response info in the library being instrumented.
   * @param statusConverter the converter from HTTP status codes to OpenTelemetry statuses.
   * @param meter the named OpenTelemetry meter to use.
   */
  AbstractHttpHandler(
      HttpExtractor<Q, P> extractor,
      @Nullable StatusCodeConverter statusConverter,
      @Nullable Meter meter) {
    checkNotNull(extractor, "extractor is required");
    checkNotNull(statusConverter, "statusConverter is required");
    checkNotNull(meter, "meter is required");
    this.extractor = extractor;
    this.statusConverter = statusConverter;
    this.meter = meter;
  }

  /**
   * A convenience method to record a {@link Event} with given parameters. This approach comes from
   * gRPC where multiple messages are sent across the same span. Each message is an event but the
   * message size measurements are recorded at the end of the span as a total of all messages. Using
   * this approach supports WebSocket (RFC 6455) and Server-Sent Events spans. In addition, message
   * events are published consistently whether messages are HTTP or gRPC or potentially other
   * protocols.
   *
   * @param span the span which this {@code Event} will be added to.
   * @param type the type of event.
   * @param uncompressedMessageSize size of the message before compressed or zero if unknown.
   * @param compressedMessageSize size of the message after compressed or zero if unknown.
   */
  static void recordMessageEvent(
      Span span,
      long id,
      AttributeValue type,
      long uncompressedMessageSize,
      long compressedMessageSize) {
    Map<String, AttributeValue> attributes = new HashMap<>();
    attributes.put(HttpTraceConstants.MSG_EVENT_ATTR_TYPE, type);
    attributes.put(HttpTraceConstants.MSG_EVENT_ATTR_ID, AttributeValue.longAttributeValue(id));
    if (compressedMessageSize > 0L) {
      attributes.put(
          HttpTraceConstants.MSG_EVENT_ATTR_COMPRESSED,
          AttributeValue.longAttributeValue(compressedMessageSize));
    }
    if (uncompressedMessageSize > 0L) {
      attributes.put(
          HttpTraceConstants.MSG_EVENT_ATTR_UNCOMPRESSED,
          AttributeValue.longAttributeValue(uncompressedMessageSize));
    }
    span.addEvent(HttpTraceConstants.MSG_EVENT_NAME, attributes);
  }

  private static void putAttributeIfNotEmptyOrNull(Span span, String key, @Nullable String value) {
    if (value != null && !value.isEmpty()) {
      span.setAttribute(key, AttributeValue.stringAttributeValue(value));
    }
  }

  /**
   * Instrument an HTTP span after a message is sent. Typically called for every chunk of request or
   * response is sent.
   *
   * @param context request specific {@link HttpRequestContext}
   * @param bytes bytes sent.
   */
  public final void handleMessageSent(HttpRequestContext context, long bytes) {
    checkNotNull(context, "context is required");
    context.addSentMessageSize(bytes);
    recordMessageEvent(
        context.getSpan(),
        context.nextSentSeqId(),
        HttpTraceConstants.MSG_EVENT_ATTR_SENT,
        bytes,
        0L);
  }

  /**
   * Instrument an HTTP span after a message is received. Typically called for every chunk of
   * request or response is received.
   *
   * @param context request specific {@link HttpRequestContext}
   * @param bytes bytes received.
   */
  public final void handleMessageReceived(HttpRequestContext context, long bytes) {
    checkNotNull(context, "context is required");
    context.addReceiveMessageSize(bytes);
    recordMessageEvent(
        context.getSpan(),
        context.nextReceivedSeqId(),
        HttpTraceConstants.MSG_EVENT_ATTR_RECEIVED,
        bytes,
        0L);
  }

  void endSpan(Span span, int httpStatus, @Nullable Throwable error) {
    span.setAttribute(
        HttpTraceConstants.HTTP_STATUS_CODE, AttributeValue.longAttributeValue(httpStatus));
    if (error != null) {
      String message = extractErrorMessage(error);
      span.setAttribute(
          HttpTraceConstants.HTTP_STATUS_TEXT, AttributeValue.stringAttributeValue(message));
      getLogger().log(Level.FINE, message, error);
    }
    span.setStatus(statusConverter.convert(httpStatus));
    span.end();
  }

  String extractSpanName(Q request) {
    String spanName = extractor.getRoute(request);
    if (spanName == null) {
      spanName = "/";
    }
    if (!spanName.startsWith("/")) {
      spanName = "/" + spanName;
    }
    return spanName;
  }

  final void addSpanRequestAttributes(Span span, Q request) {
    span.setAttribute(HttpTraceConstants.COMPONENT, HttpTraceConstants.COMPONENT_ATTR_VALUE);
    putAttributeIfNotEmptyOrNull(
        span, HttpTraceConstants.HTTP_METHOD, extractor.getMethod(request));
    putAttributeIfNotEmptyOrNull(span, HttpTraceConstants.HTTP_ROUTE, extractor.getRoute(request));
    putAttributeIfNotEmptyOrNull(span, HttpTraceConstants.HTTP_URL, extractor.getUrl(request));
    putAttributeIfNotEmptyOrNull(
        span, HttpTraceConstants.HTTP_USERAGENT, extractor.getUserAgent(request));
    putAttributeIfNotEmptyOrNull(
        span, HttpTraceConstants.HTTP_FLAVOR, extractor.getHttpFlavor(request));
    putAttributeIfNotEmptyOrNull(
        span, HttpTraceConstants.HTTP_CLIENTIP, extractor.getClientIp(request));
  }

  String extractErrorMessage(Throwable error) {
    String message = error.getMessage();
    if (!isNullOrEmpty(message)) {
      return message;
    }
    return error.getClass().getSimpleName();
  }

  protected void recordMeasurements(HttpRequestContext context, int httpCode) {
    // TODO implement method correctly after MeterSdk is functional
    try {
      meter.newMeasureBatchRecorder();
      meter.longCounterBuilder("temporary.requests." + httpCode).build();
    } catch (UnsupportedOperationException ignore) {
      // NoOp
    }
  }

  abstract Logger getLogger();

  /**
   * Retrieves {@link Span} from the {@link HttpRequestContext}.
   *
   * @param context request specific {@link HttpRequestContext}
   * @return {@link Span} associated with the request.
   */
  public Span getSpanFromContext(HttpRequestContext context) {
    checkNotNull(context, "context is required");
    return context.getSpan();
  }

  HttpRequestContext getNewContext(Span span, CorrelationContext correlationContext) {
    return new HttpRequestContext(span, correlationContext);
  }
}
