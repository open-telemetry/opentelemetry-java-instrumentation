/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0;

import ch.qos.logback.access.common.spi.IAccessEvent;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import java.net.URI;
import java.net.URISyntaxException;

public class AccessEventMapper {

  public static final String ACCESS_EVENT_NAME = "access-event";

  @SuppressWarnings({"MethodCanBeStatic", "EmptyCatch"})
  // MethodCanBeStatic: This method should eventually refer to configs in this class. So it should not
  // be static.
  // EmptyCatch: for parsing URI, the input itself is actually one URI, so there is low chance the
  // exception would be thrown. Even when it actually happened, there is not much we can do here.
  private void mapLoggingEvent(LogRecordBuilder builder, IAccessEvent accessEvent) {
    URI uri = null;
    try {
      uri = new URI(accessEvent.getRequestURI());
    } catch (URISyntaxException ignored) {
    }
    builder
        .setSeverity(Severity.UNDEFINED_SEVERITY_NUMBER)
        .setEventName(ACCESS_EVENT_NAME)
        .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, accessEvent.getMethod())
        .setAttribute(
            HttpAttributes.HTTP_RESPONSE_STATUS_CODE, Long.valueOf(accessEvent.getStatusCode()))
        .setAttribute(ClientAttributes.CLIENT_ADDRESS, accessEvent.getRemoteAddr())
        .setAttribute(ServerAttributes.SERVER_ADDRESS, accessEvent.getServerName())
        .setAttribute(
            UserAgentAttributes.USER_AGENT_ORIGINAL,
            accessEvent.getRequestHeader("HTTP User-Agent"));
    if (uri != null) {
      builder
          .setAttribute(UrlAttributes.URL_PATH, uri.getPath())
          .setAttribute(UrlAttributes.URL_SCHEME, uri.getScheme());
    }
  }

  public void emit(LoggerProvider loggerProvider, IAccessEvent event) {
    String instrumentationName = "ROOT";
    LogRecordBuilder builder =
        loggerProvider.loggerBuilder(instrumentationName).build().logRecordBuilder();
    mapLoggingEvent(builder, event);
    builder.emit();
  }
}
