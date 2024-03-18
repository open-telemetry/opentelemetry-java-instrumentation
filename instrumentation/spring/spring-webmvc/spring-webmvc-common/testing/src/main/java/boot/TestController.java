package boot;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

@Controller
public class TestController {
  @RequestMapping("/basicsecured/endpoint")
  @ResponseBody
  String secureEndpoint() {
    return HttpServerTest.controller(SUCCESS, SUCCESS::getBody);
  }

  @RequestMapping("/success")
  @ResponseBody
  String success() {
    return HttpServerTest.controller(SUCCESS, SUCCESS::getBody);
  }

  @RequestMapping("/query")
  @ResponseBody
  String queryParam(@RequestParam("some") String param) {
    return HttpServerTest.controller(QUERY_PARAM, () -> "some=" + param);
  }

  @RequestMapping("/redirect")
  @ResponseBody
  RedirectView redirect() {
    return HttpServerTest.controller(REDIRECT, () -> new RedirectView(REDIRECT.getBody()));
  }

  @RequestMapping("/error-status")
  ResponseEntity<String> error() {
    return HttpServerTest.controller(ERROR, () -> new ResponseEntity<>(ERROR.getBody(), HttpStatus.valueOf(ERROR.getStatus())));
  }

  @RequestMapping("/exception")
  ResponseEntity<String> exception() {
    return HttpServerTest.controller(EXCEPTION, () -> {
      throw new Exception(EXCEPTION.getBody());
    });
  }

  @RequestMapping("/captureHeaders")
  ResponseEntity<String> captureHeaders(@RequestHeader("X-Test-Request") String testRequestHeader) {
    return HttpServerTest.controller(CAPTURE_HEADERS, () -> ResponseEntity.ok()
        .header("X-Test-Response", testRequestHeader)
        .body(CAPTURE_HEADERS.getBody()));
  }

  @RequestMapping("/path/{id}/param")
  @ResponseBody
  String pathParam(@PathVariable("id") int id) {
    return HttpServerTest.controller(PATH_PARAM, () -> String.valueOf(id));
  }

  @RequestMapping("/child")
  @ResponseBody
  String indexedChild(@RequestParam("id") String id) {
    return HttpServerTest.controller(INDEXED_CHILD, () -> {
      INDEXED_CHILD.collectSpanAttributes(it -> Objects.equals(it, "id") ? id : null );
      return INDEXED_CHILD.getBody();
    });
  }

  @ExceptionHandler
  ResponseEntity<String> handleException(Throwable throwable) {
    return new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
