package io.opentelemetry.instrumentation.armeria.v1_3

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.ServerBuilder
import com.linecorp.armeria.testing.junit4.server.ServerRule
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import org.junit.ClassRule
import spock.lang.Shared
// We use Void as type parameter since Armeria's test extension handles lifecycle better.
abstract class AbstractArmeriaHttpServerTest extends HttpServerTest<Void> {

  abstract ServerBuilder configureServer(ServerBuilder serverBuilder)

  @Shared
  @ClassRule
  protected ServerRule server = new ServerRule() {
    @Override
    protected void configure(ServerBuilder sb) throws Exception {
      sb.service("/${SUCCESS.path}") { ctx, req ->
        return HttpResponse.of(HttpStatus.valueOf(SUCCESS.status), MediaType.PLAIN_TEXT_UTF_8, SUCCESS.body)
      }

      sb.service("/${REDIRECT.path}") {ctx, req ->
        return HttpResponse.of(ResponseHeaders.of(HttpStatus.valueOf(REDIRECT.status), HttpHeaderNames.LOCATION, REDIRECT.body))
      }

      sb.service("/${ERROR.path}") {
        return HttpResponse.of(HttpStatus.valueOf(ERROR.status), MediaType.PLAIN_TEXT_UTF_8, ERROR.body)
      }

      sb.service("/${EXCEPTION.path}") {
        throw new Exception(EXCEPTION.body)
      }

      sb.service("/path/:id/param") {

      }

      configureServer(sb)
    }
  }

  @Override
  URI buildAddress() {
    return server.httpUri()
  }


  @Override
  Void startServer(int port) {
    return null;
  }

  @Override
  void stopServer(Void unused) {
  }
}
