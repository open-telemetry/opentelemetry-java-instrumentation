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

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor;
import io.opentelemetry.trace.Tracer;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;

/** Spring bean post processor test {@link RestTemplateBeanPostProcessor} */
@RunWith(MockitoJUnitRunner.class)
public class RestTemplateBeanPostProcessorTest {
  @Mock Tracer tracer;

  RestTemplateBeanPostProcessor restTemplateBeanPostProcessor =
      new RestTemplateBeanPostProcessor(tracer);

  @Test
  @DisplayName("when processed bean is not of type RestTemplate should return object")
  public void returnsObject() {
    assertThat(
            restTemplateBeanPostProcessor.postProcessAfterInitialization(
                new Object(), "testObject"))
        .isExactlyInstanceOf(Object.class);
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should return RestTemplate")
  public void returnsRestTemplate() {
    assertThat(
            restTemplateBeanPostProcessor.postProcessAfterInitialization(
                new RestTemplate(), "testRestTemplate"))
        .isInstanceOf(RestTemplate.class);
  }

  @Test
  @DisplayName("when processed bean is of type RestTemplate should add ONE RestTemplateInterceptor")
  public void addsRestTemplateInterceptor() {
    RestTemplate restTemplate = new RestTemplate();

    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");

    assertThat(
            restTemplate.getInterceptors().stream()
                .filter(rti -> rti instanceof RestTemplateInterceptor)
                .count())
        .isEqualTo(1);
  }
}
