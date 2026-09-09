/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerSingletons.HTTP_REQUEST_PEER_ADDRESS;

import java.net.InetSocketAddress;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.stream.Attributes;
import scala.Function1;
import scala.runtime.AbstractFunction1;

/**
 * Wraps the function that {@code RequestParsing.parseRequest} builds for an http/2 connection.
 *
 * <p>Http/2 requests are assembled from the frames of a stream instead of passing through the
 * http/1.1 server blueprint, so {@link PekkoHttpServerTracer}, which records the peer address for
 * http/1.1, never sees them. The parsing function is built from the stream attributes of the
 * connection, which carry {@link PekkoHttpServerRemoteAddress}, so the peer address can be recorded
 * on every request that the function produces.
 */
public class PekkoHttp2RequestParsingWrapper extends AbstractFunction1<Object, HttpRequest> {

  private final Function1<Object, HttpRequest> parseRequest;
  private final InetSocketAddress remoteAddress;

  public static Function1<Object, HttpRequest> wrap(
      Function1<Object, HttpRequest> parseRequest, Attributes attributes) {
    InetSocketAddress remoteAddress =
        attributes
            .getAttribute(PekkoHttpServerRemoteAddress.class)
            .map(PekkoHttpServerRemoteAddress::getAddress)
            .orElse(null);
    if (remoteAddress == null) {
      return parseRequest;
    }
    return new PekkoHttp2RequestParsingWrapper(parseRequest, remoteAddress);
  }

  private PekkoHttp2RequestParsingWrapper(
      Function1<Object, HttpRequest> parseRequest, InetSocketAddress remoteAddress) {
    this.parseRequest = parseRequest;
    this.remoteAddress = remoteAddress;
  }

  @Override
  public HttpRequest apply(Object subStream) {
    HttpRequest request = parseRequest.apply(subStream);
    HTTP_REQUEST_PEER_ADDRESS.set(request, remoteAddress);
    return request;
  }
}
