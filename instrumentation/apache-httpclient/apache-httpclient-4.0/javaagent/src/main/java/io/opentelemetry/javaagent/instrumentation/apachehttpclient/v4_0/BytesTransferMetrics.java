package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import java.util.concurrent.atomic.AtomicLong;

public class BytesTransferMetrics {
  private final AtomicLong bytesIn = new AtomicLong();

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
   * Get request content length, priority is given if explicit content-length is present like in
   * case when the request is not chunked. For chunked response, since the response is lazily
   * fetched, there is no way for us to be able to compute the response content length otherwise.
   *
   * @return content-length of request, null if content-length is not applicable.
   */
  public Long getRequestContentLength() {
    if (requestContentLength >= 0) {
      return requestContentLength;
    }
    return null;
  }

  /**
   * Get response content length, priority is given if explicit content-length is present like in
   * case when the response is not chunked, else value is computed using the bytes read.
   *
   * @return content-length of response, null if content-length is not applicable.
   */
  public Long getResponseContentLength() {
    if (responseContentLength >= 0) {
      return responseContentLength;
    }
    long bytesRead = bytesIn.get();
    if (bytesRead > 0) {
      return bytesRead;
    }
    return null;
  }
}
