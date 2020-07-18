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

public interface GrpcClientSemanticConvention {
  void end();

  Span getSpan();

  
  /**
   * Sets a value for rpc.service
   * @param rpcService The service name, must be equal to the $service part in the span name.
   */
  GrpcClientSemanticConvention setRpcService(String rpcService);

  /**
   * Sets a value for net.transport
   * @param netTransport Transport protocol used. See note below.
   */
  GrpcClientSemanticConvention setNetTransport(String netTransport);

  /**
   * Sets a value for net.peer.ip
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  GrpcClientSemanticConvention setNetPeerIp(String netPeerIp);

  /**
   * Sets a value for net.peer.name
   * @param netPeerName Remote hostname or similar, see note below.
   */
  GrpcClientSemanticConvention setNetPeerName(String netPeerName);

  /**
   * Sets a value for net.host.ip
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  GrpcClientSemanticConvention setNetHostIp(String netHostIp);

  /**
   * Sets a value for net.host.port
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  GrpcClientSemanticConvention setNetHostPort(long netHostPort);

  /**
   * Sets a value for net.host.name
   * @param netHostName Local hostname or similar, see note below.
   */
  GrpcClientSemanticConvention setNetHostName(String netHostName);

  /**
   * Sets a value for net.peer.port
   * @param netPeerPort It describes the server port the client is connecting to.
   */
  GrpcClientSemanticConvention setNetPeerPort(long netPeerPort);

}