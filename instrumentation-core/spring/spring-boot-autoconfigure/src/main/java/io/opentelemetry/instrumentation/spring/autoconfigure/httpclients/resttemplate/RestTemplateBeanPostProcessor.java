/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/** Adds Open Telemetry instrumentation to RestTemplate beans after initialization */
public final class RestTemplateBeanPostProcessor implements BeanPostProcessor {

  private final Tracer tracer;

  public RestTemplateBeanPostProcessor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof RestTemplate)) {
      return bean;
    }

    RestTemplate restTemplate = (RestTemplate) bean;
    addRestTemplateInterceptorIfNotPresent(restTemplate);
    return restTemplate;
  }

  private void addRestTemplateInterceptorIfNotPresent(RestTemplate restTemplate) {

    List<ClientHttpRequestInterceptor> restTemplateInterceptors = restTemplate.getInterceptors();
    if (restTemplateInterceptors.stream()
        .noneMatch(inteceptor -> inteceptor instanceof RestTemplateInterceptor)) {
      restTemplateInterceptors.add(0, new RestTemplateInterceptor(tracer));
    }
  }
}
