/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.Validator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class FilteredAppConfig implements WebMvcConfigurer {

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {}

  @Override
  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    configurer
        .favorPathExtension(false)
        .favorParameter(true)
        .ignoreAcceptHeader(true)
        .useJaf(false)
        .defaultContentTypeStrategy(webRequest -> Collections.singletonList(MediaType.TEXT_PLAIN));
  }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {}

  @Override
  public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {}

  @Override
  public void addFormatters(FormatterRegistry registry) {}

  @Override
  public void addInterceptors(InterceptorRegistry registry) {}

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {}

  @Override
  public void addCorsMappings(CorsRegistry registry) {}

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {}

  @Override
  public void configureViewResolvers(ViewResolverRegistry registry) {}

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {}

  @Override
  public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {}

  @Override
  public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {}

  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {}

  @Override
  public void configureHandlerExceptionResolvers(
      List<HandlerExceptionResolver> exceptionResolvers) {}

  @Override
  public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {}

  @Override
  public Validator getValidator() {
    return null;
  }

  @Override
  public MessageCodesResolver getMessageCodesResolver() {
    return null;
  }

  @Bean
  HttpMessageConverter<Map<String, Object>> createPlainMapMessageConverter() {

    return new AbstractHttpMessageConverter<Map<String, Object>>(MediaType.TEXT_PLAIN) {

      @Override
      protected boolean supports(Class<?> clazz) {
        return Map.class.isAssignableFrom(clazz);
      }

      @Nullable
      @Override
      protected Map<String, Object> readInternal(
          Class<? extends Map<String, Object>> clazz, HttpInputMessage inputMessage) {
        return null;
      }

      @Override
      protected void writeInternal(
          Map<String, Object> stringObjectMap, HttpOutputMessage outputMessage) throws IOException {
        StreamUtils.copy(
            (String) stringObjectMap.get("message"),
            StandardCharsets.UTF_8,
            outputMessage.getBody());
      }
    };
  }
}
