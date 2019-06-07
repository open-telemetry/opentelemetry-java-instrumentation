package datadog.trace.instrumentation.springwebflux.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Wrapper class for ClientResponse that adds Context to the body Publisher
 */
public class ClientResponseWrapper implements ClientResponse {

  private final ClientResponse clientResponse;
  private final Context context;

  public ClientResponseWrapper(final ClientResponse clientResponse, final Context context) {
    this.clientResponse = clientResponse;
    this.context = context;
  }

  @Override
  public HttpStatus statusCode() {
    return clientResponse.statusCode();
  }

  @Override
  public Headers headers() {
    return clientResponse.headers();
  }

  @Override
  public MultiValueMap<String, ResponseCookie> cookies() {
    return clientResponse.cookies();
  }

  @Override
  public <T> T body(final BodyExtractor<T, ? super ClientHttpResponse> extractor) {
    return clientResponse.body(extractor);
  }

  @Override
  public <T> Mono<T> bodyToMono(final Class<? extends T> elementClass) {
    return clientResponse.<T>bodyToMono(elementClass).subscriberContext(context);
  }

  @Override
  public <T> Mono<T> bodyToMono(final ParameterizedTypeReference<T> typeReference) {
    return clientResponse.<T>bodyToMono(typeReference).subscriberContext(context);
  }

  @Override
  public <T> Flux<T> bodyToFlux(final Class<? extends T> elementClass) {
    return clientResponse.<T>bodyToFlux(elementClass).subscriberContext(context);
  }

  @Override
  public <T> Flux<T> bodyToFlux(final ParameterizedTypeReference<T> typeReference) {
    return clientResponse.<T>bodyToFlux(typeReference).subscriberContext(context);
  }

  @Override
  public <T> Mono<ResponseEntity<T>> toEntity(final Class<T> bodyType) {
    return clientResponse.toEntity(bodyType);
  }

  @Override
  public <T> Mono<ResponseEntity<T>> toEntity(final ParameterizedTypeReference<T> typeReference) {
    return clientResponse.toEntity(typeReference);
  }

  @Override
  public <T> Mono<ResponseEntity<List<T>>> toEntityList(final Class<T> elementType) {
    return clientResponse.toEntityList(elementType);
  }

  @Override
  public <T> Mono<ResponseEntity<List<T>>> toEntityList(
    final ParameterizedTypeReference<T> typeReference) {
    return clientResponse.toEntityList(typeReference);
  }

  /**
   * ClientResponseWrapper is based on the ClientResponse from
   * spring-webflux-5.0.0.RELEASE. Since spring-webflux 5.1 ClientResponse
   * contains extra methods like rawStatusCode and gives methodNotFound
   * exceptions at runtime if used in a project with the latest spring-webflux
   * 5.1 or higher.
   * <p>
   * See https://docs.spring.io/spring/docs/5.1.x/javadoc-api/org/springframework/web/reactive/function/client/ClientResponse.html#rawStatusCode--
   */
  public int rawStatusCode() {
    return clientResponse.statusCode().value();
  }
}
