/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package filter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.format.FormatterRegistry
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.util.StreamUtils
import org.springframework.validation.MessageCodesResolver
import org.springframework.validation.Validator
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.HandlerMethodReturnValueHandler
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

import java.nio.charset.StandardCharsets

@SpringBootApplication
class FilteredAppConfig implements WebMvcConfigurer {

  @Override
  void configurePathMatch(PathMatchConfigurer configurer) {}

  @Override
  void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    configurer.favorPathExtension(false)
      .favorParameter(true)
      .ignoreAcceptHeader(true)
      .useJaf(false)
      .defaultContentTypeStrategy(new ContentNegotiationStrategy() {
        @Override
        List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) throws HttpMediaTypeNotAcceptableException {
          return [MediaType.TEXT_PLAIN]
        }
      })
  }

  @Override
  void configureAsyncSupport(AsyncSupportConfigurer configurer) {}

  @Override
  void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {}

  @Override
  void addFormatters(FormatterRegistry registry) {}

  @Override
  void addInterceptors(InterceptorRegistry registry) {}

  @Override
  void addResourceHandlers(ResourceHandlerRegistry registry) {}

  @Override
  void addCorsMappings(CorsRegistry registry) {}

  @Override
  void addViewControllers(ViewControllerRegistry registry) {}

  @Override
  void configureViewResolvers(ViewResolverRegistry registry) {}

  @Override
  void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {}

  @Override
  void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {}

  @Override
  void configureMessageConverters(List<HttpMessageConverter<?>> converters) {}

  @Override
  void extendMessageConverters(List<HttpMessageConverter<?>> converters) {}

  @Override
  void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {}

  @Override
  void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {}

  @Override
  Validator getValidator() {
    return null
  }

  @Override
  MessageCodesResolver getMessageCodesResolver() {
    return null
  }

  @Bean
  HttpMessageConverter<Map<String, Object>> createPlainMapMessageConverter() {
    return new AbstractHttpMessageConverter<Map<String, Object>>(MediaType.TEXT_PLAIN) {

      @Override
      protected boolean supports(Class<?> clazz) {
        return Map.isAssignableFrom(clazz)
      }

      @Override
      protected Map<String, Object> readInternal(Class<? extends Map<String, Object>> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null
      }

      @Override
      protected void writeInternal(Map<String, Object> stringObjectMap, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        StreamUtils.copy(stringObjectMap.get("message") as String, StandardCharsets.UTF_8, outputMessage.getBody())
      }
    }
  }
}
