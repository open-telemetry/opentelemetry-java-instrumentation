package datadog.trace.instrumentation.apachehttpclient;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.Getter;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.AbstractHttpMessage;

/** Wraps HttpHost and HttpRequest into a HttpUriRequest for decorators and injectors */
@Getter
public class HostAndRequestAsHttpUriRequest extends AbstractHttpMessage implements HttpUriRequest {

  private final String method;
  private final RequestLine requestLine;
  private final ProtocolVersion protocolVersion;
  private final java.net.URI URI;

  private final HttpRequest actualRequest;

  public HostAndRequestAsHttpUriRequest(final HttpHost httpHost, final HttpRequest httpRequest) {

    method = httpRequest.getRequestLine().getMethod();
    requestLine = httpRequest.getRequestLine();
    protocolVersion = requestLine.getProtocolVersion();

    URI calculatedURI;
    try {
      calculatedURI = new URI(httpHost.toURI() + httpRequest.getRequestLine().getUri());
    } catch (final URISyntaxException e) {
      calculatedURI = null;
    }
    URI = calculatedURI;
    actualRequest = httpRequest;
  }

  @Override
  public void abort() throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAborted() {
    return false;
  }

  @Override
  public void addHeader(final String name, final String value) {
    actualRequest.addHeader(name, value);
  }
}
