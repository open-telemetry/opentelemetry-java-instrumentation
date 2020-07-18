/*
 * Copyright The OpenTelemetry Authors
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
package io.opentelemetry.auto.typedspan;

import io.opentelemetry.trace.Span;

public interface HttpSemanticConvention {
  void end();

  Span getSpan();

  
  /**
   * Sets a value for net.transport
   * @param netTransport Transport protocol used. See note below..
   */
  public HttpSemanticConvention setNetTransport(String netTransport);

  /**
   * Sets a value for net.peer.ip
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  public HttpSemanticConvention setNetPeerIp(String netPeerIp);

  /**
   * Sets a value for net.peer.port
   * @param netPeerPort Remote port number..
   */
  public HttpSemanticConvention setNetPeerPort(long netPeerPort);

  /**
   * Sets a value for net.peer.name
   * @param netPeerName Remote hostname or similar, see note below..
   */
  public HttpSemanticConvention setNetPeerName(String netPeerName);

  /**
   * Sets a value for net.host.ip
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host..
   */
  public HttpSemanticConvention setNetHostIp(String netHostIp);

  /**
   * Sets a value for net.host.port
   * @param netHostPort Like `net.peer.port` but for the host port..
   */
  public HttpSemanticConvention setNetHostPort(long netHostPort);

  /**
   * Sets a value for net.host.name
   * @param netHostName Local hostname or similar, see note below..
   */
  public HttpSemanticConvention setNetHostName(String netHostName);

  /**
   * Sets a value for http.method
   * @param httpMethod HTTP request method..
   */
  public HttpSemanticConvention setHttpMethod(String httpMethod);

  /**
   * Sets a value for http.url
   * @param httpUrl Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`. Usually the fragment is not transmitted over HTTP, but if it is known, it should be included nevertheless..
   */
  public HttpSemanticConvention setHttpUrl(String httpUrl);

  /**
   * Sets a value for http.target
   * @param httpTarget The full request target as passed in a HTTP request line or equivalent..
   */
  public HttpSemanticConvention setHttpTarget(String httpTarget);

  /**
   * Sets a value for http.host
   * @param httpHost The value of the [HTTP host header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not present, this attribute should be the same..
   */
  public HttpSemanticConvention setHttpHost(String httpHost);

  /**
   * Sets a value for http.scheme
   * @param httpScheme The URI scheme identifying the used protocol..
   */
  public HttpSemanticConvention setHttpScheme(String httpScheme);

  /**
   * Sets a value for http.status_code
   * @param httpStatusCode [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6)..
   */
  public HttpSemanticConvention setHttpStatusCode(long httpStatusCode);

  /**
   * Sets a value for http.status_text
   * @param httpStatusText [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2)..
   */
  public HttpSemanticConvention setHttpStatusText(String httpStatusText);

  /**
   * Sets a value for http.flavor
   * @param httpFlavor Kind of HTTP protocol used.
   * <p> If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor` is `QUIC`, in which case `IP.UDP` is assumed.
   */
  public HttpSemanticConvention setHttpFlavor(String httpFlavor);

  /**
   * Sets a value for http.user_agent
   * @param httpUserAgent Value of the [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client..
   */
  public HttpSemanticConvention setHttpUserAgent(String httpUserAgent);

}