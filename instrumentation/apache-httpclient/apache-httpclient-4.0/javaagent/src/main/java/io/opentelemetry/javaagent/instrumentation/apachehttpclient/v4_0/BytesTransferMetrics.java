/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import java.util.concurrent.atomic.AtomicLong;

public class BytesTransferMetrics {
  private final AtomicLong bytesOut = new AtomicLong();

  private long requestContentLength = -1;

  private long responseContentLength = -1;

  /**
   * Set content-length of request sent by the client. In case of chunked request, the value
   * returned by the client is -1.
   *
   * @param contentLength request content length or -ve value for chunked.
   */
  public void setRequestContentLength(long contentLength) {
    this.requestContentLength = contentLength;
  }

  /**
   * Set content-length of response received by the client. In case of chunked response, the value
   * returned by the client is -1.
   *
   * @param contentLength response content length or -ve value for chunked.
   */
  public void setResponseContentLength(long contentLength) {
    this.responseContentLength = contentLength;
  }

  /**
   * Add request bytes produced. This may not always represent the request size, for example in case
   * when connection is closed while writing request, this will repsent the bytes written till this
   * point.
   *
   * @param byteLength bytes written from request.
   */
  public void addRequestBytes(int byteLength) {
    bytesOut.addAndGet(byteLength);
  }

  /**
   * Get request content length, priority is given if explicit content-length is present like in
   * case when the request is not chunked, else value is computed using the bytes written.
   *
   * @return content-length of request, null if content-length is not applicable.
   */
  public Long getRequestContentLength() {
    if (requestContentLength >= 0) {
      return requestContentLength;
    }
    long bytesWritten = bytesOut.get();
    if (bytesWritten > 0) {
      return bytesWritten;
    }
    return null;
  }

  /**
   * Get response content length when explicit content-length is present like in case when the
   * response is not chunked. Since the response is read lazily there is no way we can find the
   * response content length when the response is chunked.
   *
   * @return content-length of response, null if content-length is not applicable.
   */
  public Long getResponseContentLength() {
    if (responseContentLength >= 0) {
      return responseContentLength;
    }
    return null;
  }
}
