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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;

/** Spring bean post processor test {@link RestTemplateBeanPostProcessor} */
@RunWith(MockitoJUnitRunner.class)
public class RestTemplateBeanPostProcessorTest {

  @InjectMocks RestTemplateBeanPostProcessor restTemplateBeanPostProcessor;

  @Test
  public void should_return_object_if_processed_bean_is_not_of_type_RestTemplate() {
    assertEquals(
        restTemplateBeanPostProcessor
            .postProcessAfterInitialization(new Object(), "testObject")
            .getClass(),
        Object.class);
  }

  @Test
  public void should_return_RestTemplate_if_processed_bean_is_of_type_RestTemplate() {
    assertTrue(
        restTemplateBeanPostProcessor.postProcessAfterInitialization(
                new RestTemplate(), "testRestTemplate")
            instanceof RestTemplate);
  }

  @Test
  public void should_add_ONE_RestTemplateInterceptor_if_processed_bean_is_of_type_RestTemplate() {
    RestTemplate restTemplate = new RestTemplate();

    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");
    restTemplateBeanPostProcessor.postProcessAfterInitialization(restTemplate, "testRestTemplate");

    assertEquals(
        restTemplate.getInterceptors().stream()
            .filter(rti -> rti instanceof RestTemplateInterceptor)
            .count(),
        1);
  }
}
