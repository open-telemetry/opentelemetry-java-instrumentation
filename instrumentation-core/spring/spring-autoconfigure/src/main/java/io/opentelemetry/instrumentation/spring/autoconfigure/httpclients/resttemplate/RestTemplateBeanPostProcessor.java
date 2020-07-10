package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor;
import io.opentelemetry.trace.Tracer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.client.RestTemplate;

/**
 * BeanProcessor Implementation inspired by: <br>
 *
 * @see <a href=
 *     "https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientAutoConfiguration.java">
 *     spring-cloud-sleuth-core </a>
 */
public final class RestTemplateBeanPostProcessor implements BeanPostProcessor {

  private final Tracer tracer;

  public RestTemplateBeanPostProcessor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof RestTemplate) {
      RestTemplate restTemplate = (RestTemplate) bean;
      restTemplate.getInterceptors().add(new RestTemplateInterceptor(tracer));
      return restTemplate;
    }

    return bean;
  }
}