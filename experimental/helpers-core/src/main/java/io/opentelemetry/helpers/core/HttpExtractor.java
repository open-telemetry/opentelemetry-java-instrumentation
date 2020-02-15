package io.opentelemetry.helpers.core;

/**
 * An adaptor to extract information from request and response.
 *
 * <p>Please refer to this <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/data-http.md">document</a>
 * for more information about the HTTP attributes recorded in Open Telemetry.
 *
 * @param <Q> the HTTP request entity.
 * @param <P> the HTTP response entity.
 */
public interface HttpExtractor<Q, P> {

  /**
   * Returns the request method for use as the value of the <code>http.method</code> span attribute.
   *
   * @param request the HTTP request
   * @return the HTTP method
   */
  String getMethod(Q request);

  /**
   * Returns the request URL for use as the value of the <code>http.url</code> span attribute.
   *
   * @param request the HTTP request
   * @return the request URL
   */
  String getUrl(Q request);

  /**
   * Returns the request route for use as the span name. This should be in the format <code>
   * /users/:userID</code> or else the URI path.
   *
   * @param request the HTTP request
   * @return the request route
   */
  String getRoute(Q request);

  /**
   * Returns the request user agent.
   *
   * @param request the HTTP request
   * @return the request user agent
   */
  String getUserAgent(Q request);

  /**
   * Returns the HTTP protocol version used by the connection.
   *
   * @param request the HTTP request
   * @return the HTTP flavor
   */
  String getHttpFlavor(Q request);

  /**
   * Returns the IP address of the calling client (Server-side only).
   *
   * @param request the HTTP request
   * @return the IP address
   */
  String getClientIp(Q request);

  /**
   * Returns the response status code for use as the value of the <code>http.status_code</code> span
   * attribute. If the response is null, this method should return {@code 0}.
   *
   * @param response the HTTP response
   * @return the response status code
   */
  int getStatusCode(P response);
}
