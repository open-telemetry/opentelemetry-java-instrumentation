import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.http.HttpMethod

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import static java.util.concurrent.TimeUnit.SECONDS

class OkHttp3AsyncTest extends OkHttp3Test {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null
    def request = new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
      .headers(Headers.of(headers))
      .build()

    AtomicReference<Response> responseRef = new AtomicReference()
    AtomicReference<Exception> exRef = new AtomicReference()
    def latch = new CountDownLatch(1)

    client.newCall(request).enqueue(new Callback() {
      void onResponse(Call call, Response response) {
        responseRef.set(response)
        callback?.call()
        latch.countDown()
      }

      void onFailure(Call call, IOException e) {
        exRef.set(e)
        latch.countDown()
      }
    })
    latch.await(10, SECONDS)
    if (exRef.get() != null) {
      throw exRef.get()
    }
    return responseRef.get().code()
  }
}
