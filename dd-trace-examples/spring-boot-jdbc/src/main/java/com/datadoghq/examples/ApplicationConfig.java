package com.datadoghq.examples;

import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@EnableAutoConfiguration
public class ApplicationConfig extends WebMvcConfigurerAdapter {

  @Bean
  public HttpClientBuilder getHttpClientBuilder() {
    return HttpClientBuilder.create();
  }
}
