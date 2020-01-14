import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse

class GoogleHttpClientAsyncTest extends AbstractGoogleHttpClientTest {
  @Override
  HttpResponse executeRequest(HttpRequest request) {
    return request.executeAsync().get()
  }
}
