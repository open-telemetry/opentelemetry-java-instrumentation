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

package io.opentelemetry.helpers.core;

/** Defines constants for all semantic convention names. */
public class SemanticConventions {

  /** Transport protocol used. */
  public static final String NET_TRANSPORT = "net.transport";
  /** Remote address of the peer (dotted decimal for IPv4 or RFC5952 for IPv6). */
  public static final String NET_PEER_IP = "net.peer.ip";
  /** Remote port number as an integer. E.g., 80. */
  public static final String NET_PEER_PORT = "net.peer.port";
  /** Remote hostname or similar. */
  public static final String NET_PEER_NAME = "net.peer.name";
  /** Like net.peer.ip but for the host IP. Useful in case of a multi-IP host. */
  public static final String NET_HOST_IP = "net.host.ip";
  /** Like net.peer.port but for the host port. */
  public static final String NET_HOST_PORT = "net.host.port";
  /** Local hostname or similar. */
  public static final String NET_HOST_NAME = "net.host.name";
  /** Username or client_id extracted from the access token or Authorization header. */
  public static final String ENDUSER_ID = "enduser.id";
  /** Actual/assumed role the client is making the request under. */
  public static final String ENDUSER_ROLE = "enduser.role";
  /** Scopes or granted authorities the client currently possesses. */
  public static final String ENDUSER_SCOPE = "enduser.scope";
  /** Denotes the type of the span. */
  public static final String COMPONENT = "component";
  /** HTTP request method. E.g. "GET". */
  public static final String HTTP_METHOD = "http.method";
  /** Full HTTP request URL in the form scheme://host[:port]/path?query[#fragment]. */
  public static final String HTTP_URL = "http.url";
  /** The full request target as passed in a HTTP request line or equivalent. */
  public static final String HTTP_TARGET = "http.target";
  /** The value of the HTTP host header. */
  public static final String HTTP_HOST = "http.host";
  /** The URI scheme identifying the used protocol: "http" or "https". */
  public static final String HTTP_SCHEME = "http.scheme";
  /** HTTP response status code. E.g. 200 (integer) If and only if one was received/sent. */
  public static final String HTTP_STATUS_CODE = "http.status_code";
  /** HTTP reason phrase. E.g. "OK" */
  public static final String HTTP_STATUS_TEXT = "http.status_text";
  /** Kind of HTTP protocol used: "1.0", "1.1", "2", "SPDY" or "QUIC". */
  public static final String HTTP_FLAVOR = "http.flavor";
  /** The primary server name of the matched virtual host. Usually obtained via configuration. */
  public static final String HTTP_SERVER_NAME = "http.server_name";
  /** The matched route (path template). */
  public static final String HTTP_ROUTE = "http.route";
  /** The IP address of the original client behind all proxies, if known. */
  public static final String HTTP_CLIENT_IP = "http.client_ip";
  /** Value of the HTTP "User-Agent" header sent by the client. */
  public static final String HTTP_USER_AGENT = "http.user_agent";
  /** The service name, must be equal to the $service part in the span name. */
  public static final String RPC_SERVICE = "rpc.service";
  /** Message span event attribute with value "SENT" or "RECEIVED". */
  public static final String MESSAGE_TYPE = "message.type";
  /**
   * Message span event attribute starting from 1 for each of sent messages and received messages.
   */
  public static final String MESSAGE_ID = "message.id";
  /** Message span event attribute for compressed size. */
  public static final String MESSAGE_COMPRESSED_SIZE = "message.compressed_size";
  /** Message span event attribute for uncompressed size. */
  public static final String MESSAGE_UNCOMPRESSED_SIZE = "message.uncompressed_size";
  /** Message span event attribute for content or body. */
  public static final String MESSAGE_CONTENT = "message.content";
  /** Database type. For any SQL database, "sql". For others, the lower-case database category. */
  public static final String DB_TYPE = "db.type";
  /** Database instance name. */
  public static final String DB_INSTANCE = "db.instance";
  /** Database statement for the given database type. */
  public static final String DB_STATEMENT = "db.statement";
  /** Username for accessing database. */
  public static final String DB_USER = "db.user";
  /** JDBC substring like "mysql://db.example.com:3306" */
  public static final String DB_URL = "db.url";
  /** The type or "kind" of an error. E.g., "Exception", "OSError" */
  public static final String ERROR_KIND = "error.kind";
  /** A concise, human-readable, one-line message explaining the event. */
  public static final String ERROR_MESSAGE = "error.message";
  /** For languages that support such a thing (e.g., Java, Python), the actual object. */
  public static final String ERROR_OBJECT = "error.object";
  /** A stack trace in platform-conventional format; may or may not pertain to an error. */
  public static final String ERROR_STACK = "error.stack";

  private SemanticConventions() {}
}
