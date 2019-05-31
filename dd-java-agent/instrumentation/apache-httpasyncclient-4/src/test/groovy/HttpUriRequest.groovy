import org.apache.http.client.methods.HttpRequestBase

class HttpUriRequest extends HttpRequestBase {

  private final String methodName

  HttpUriRequest(final String methodName, final URI uri) {
    this.methodName = methodName
    setURI(uri)
  }

  @Override
  String getMethod() {
    return methodName
  }
}
