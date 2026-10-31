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
import scala.Product;
import scala.runtime.AbstractFunction1;

/**
 * Wraps the function that {@code RequestParsing.parseRequest} builds for an http/2 connection.
 *
 * <p>Http/2 requests are assembled from the frames of a stream instead of passing through the
 * http/1.1 server blueprint, so {@link PekkoHttpServerTracer}, which records the peer address for
 * http/1.1, never sees them. The parsing function is built from the stream attributes of the
 * connection, which carry {@link PekkoHttpServerRemoteAddress}, so the peer address can be recorded
 * on every request that the function produces.
 *
 * <p>In pekko-http 1.x the function returns the {@link HttpRequest}, pekko-http 2.x wraps it in a
 * {@code RequestParsing.OkRequest}, or returns a {@code RequestParsing.BadRequest} when parsing
 * fails. {@code OkRequest} does not exist in 1.x, so it is unwrapped as the single element case
 * class that it is rather than referenced by name.
 */
public class PekkoHttp2RequestParsingWrapper extends AbstractFunction1<Object, Object> {

  private final Function1<Object, Object> parseRequest;
  private final InetSocketAddress remoteAddress;

  public static Function1<Object, Object> wrap(
      Function1<Object, Object> parseRequest, Attributes attributes) {
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
      Function1<Object, Object> parseRequest, InetSocketAddress remoteAddress) {
    this.parseRequest = parseRequest;
    this.remoteAddress = remoteAddress;
  }

  @Override
  public Object apply(Object subStream) {
    Object result = parseRequest.apply(subStream);
    HttpRequest request = getRequest(result);
    if (request != null) {
      HTTP_REQUEST_PEER_ADDRESS.set(request, remoteAddress);
    }
    return result;
  }

  private static HttpRequest getRequest(Object result) {
    if (result instanceof HttpRequest) {
      return (HttpRequest) result;
    }
    if (result instanceof Product) {
      Product product = (Product) result;
      if (product.productArity() == 1 && product.productElement(0) instanceof HttpRequest) {
        return (HttpRequest) product.productElement(0);
      }
    }
    return null;
  }
}
