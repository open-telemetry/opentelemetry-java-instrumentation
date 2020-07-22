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

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.opentelemetry.instrumentation.springwebflux.client.WebClientTracingFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.reactive.function.client.WebClient;

/** Spring bean post processor test {@link WebClientBeanPostProcessor} */
@RunWith(MockitoJUnitRunner.class)
public class WebClientBeanPostProcessorTest {

  @InjectMocks WebClientBeanPostProcessor webClientBeanPostProcessor;

  @Test
  public void
      should_return_object_if_processed_bean_is_not_of_type_web_client_or_web_client_builder() {
    assertEquals(
        webClientBeanPostProcessor
            .postProcessAfterInitialization(new Object(), "testObject")
            .getClass(),
        Object.class);
  }

  @Test
  public void should_return_web_client_if_processed_bean_is_of_type_web_client() {
    WebClient webClient = WebClient.create();

    assertTrue(
        webClientBeanPostProcessor.postProcessAfterInitialization(webClient, "testWebClient")
            instanceof WebClient);
  }

  @Test
  public void should_return_web_client_builder_if_processed_bean_is_of_type_web_client_builder() {
    WebClient.Builder webClientBuilder = WebClient.builder();

    assertTrue(
        webClientBeanPostProcessor.postProcessAfterInitialization(
                webClientBuilder, "testWebClientBuilder")
            instanceof WebClient.Builder);
  }

  @Test
  public void should_add_ONE_exchange_filter_to_web_client() {
    WebClient webClient = WebClient.create();
    Object processedWebClient =
        webClientBeanPostProcessor.postProcessAfterInitialization(webClient, "testWebClient");

    assertTrue(processedWebClient instanceof WebClient);
    ((WebClient) processedWebClient)
        .mutate()
        .filters(
            functions -> {
              assertEquals(
                  functions.stream().filter(wctf -> wctf instanceof WebClientTracingFilter).count(),
                  1);
            });
  }

  @Test
  public void should_add_ONE_exchange_filter_to_web_client_builder() {

    WebClient.Builder webClientBuilder = WebClient.builder();
    webClientBeanPostProcessor.postProcessAfterInitialization(
        webClientBuilder, "testWebClientBuilder");
    webClientBeanPostProcessor.postProcessAfterInitialization(
        webClientBuilder, "testWebClientBuilder");
    webClientBeanPostProcessor.postProcessAfterInitialization(
        webClientBuilder, "testWebClientBuilder");

    webClientBuilder.filters(
        functions -> {
          assertEquals(
              functions.stream().filter(wctf -> wctf instanceof WebClientTracingFilter).count(), 1);
        });
  }
}
