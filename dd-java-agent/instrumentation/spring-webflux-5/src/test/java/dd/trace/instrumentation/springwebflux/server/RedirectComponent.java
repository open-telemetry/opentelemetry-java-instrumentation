package dd.trace.instrumentation.springwebflux.server;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

// Need to keep this in Java because groovy creates crazy proxies around lambdas
@Component
public class RedirectComponent {
  @Bean
  public RouterFunction<ServerResponse> redirectRouterFunction() {
    return route(
        GET("/double-greet-redirect"),
        req -> ServerResponse.temporaryRedirect(URI.create("/double-greet")).build());
  }
}
