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

import io.opentelemetry.trace.AttributeValue;

/** Constants for standard Open Telemetry HTTP attributes. */
public class HttpTraceConstants {

  public static final String INSTRUMENTATION_LIB_ID = "io.opentelemetry.contrib.http";

  public static final String COMPONENT = "component";
  public static final String COMPONENT_VALUE = "http";
  public static final String HTTP_METHOD = "http.method";
  public static final String HTTP_URL = "http.url";
  public static final String HTTP_TARGET = "http.target";
  public static final String HTTP_HOST = "http.host";
  public static final String HTTP_SCHEME = "http.scheme";
  public static final String HTTP_STATUS_CODE = "http.status_code";
  public static final String HTTP_STATUS_TEXT = "http.status_text";
  public static final String HTTP_FLAVOR = "http.flavor";
  public static final String HTTP_FLAVOR_1_0 = "1.0";
  public static final String HTTP_FLAVOR_1_1 = "1.1";
  public static final String HTTP_FLAVOR_2 = "2";
  public static final String HTTP_FLAVOR_SPDY = "SPDY";
  public static final String HTTP_FLAVOR_QUIC = "QUIC";
  public static final String HTTP_SERVERNAME = "http.server_name";
  public static final String HOST_NAME = "host.name";
  public static final String HOST_PORT = "host.port";
  public static final String HTTP_ROUTE = "http.route";
  public static final String HTTP_CLIENTIP = "http.client_ip";
  public static final String HTTP_USERAGENT = "http.user_agent";
  public static final AttributeValue COMPONENT_ATTR_VALUE =
      AttributeValue.stringAttributeValue(COMPONENT_VALUE);

  public static final String MSG_EVENT_NAME = "message";
  public static final String MSG_EVENT_ATTR_TYPE = "message.type";
  public static final String MSG_EVENT_TYPE_SENT = "SENT";
  public static final String MSG_EVENT_TYPE_RECEIVED = "RECEIVED";
  public static final String MSG_EVENT_ATTR_ID = "message.id";
  public static final String MSG_EVENT_ATTR_COMPRESSED = "message.compressed_size";
  public static final String MSG_EVENT_ATTR_UNCOMPRESSED = "message.uncompressed_size";
  public static final AttributeValue MSG_EVENT_ATTR_SENT =
      AttributeValue.stringAttributeValue(MSG_EVENT_TYPE_SENT);
  public static final AttributeValue MSG_EVENT_ATTR_RECEIVED =
      AttributeValue.stringAttributeValue(MSG_EVENT_TYPE_RECEIVED);

  public static final String LINK_TYPE = "link.type";
  public static final String LINK_ORIGINATING = "originating";
  public static final AttributeValue LINK_ATTR_ORIGINATING =
      AttributeValue.stringAttributeValue(LINK_ORIGINATING);

  private HttpTraceConstants() {}
}
