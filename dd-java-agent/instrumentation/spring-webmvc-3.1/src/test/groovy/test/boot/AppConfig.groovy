package test.boot

import org.apache.catalina.connector.Connector
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

@SpringBootApplication
class AppConfig extends WebMvcConfigurerAdapter {

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

  @Bean
  EmbeddedServletContainerFactory servletContainerFactory() {
    def factory = new TomcatEmbeddedServletContainerFactory()

    factory.addConnectorCustomizers(
      new TomcatConnectorCustomizer() {
        @Override
        void customize(final Connector connector) {
          connector.setEnableLookups(true)
        }
      })

    return factory
  }
}
