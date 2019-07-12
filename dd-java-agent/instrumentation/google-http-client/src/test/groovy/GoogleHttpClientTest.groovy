import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse

class GoogleHttpClientTest extends AbstractGoogleHttpClientTest {
  @Override
  HttpResponse executeRequest(HttpRequest request) {
    return request.execute()
  }
}
