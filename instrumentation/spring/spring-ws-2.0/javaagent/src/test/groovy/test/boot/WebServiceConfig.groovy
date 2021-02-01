/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import org.springframework.context.ApplicationContext
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.ws.config.annotation.EnableWs
import org.springframework.ws.config.annotation.WsConfigurerAdapter
import org.springframework.ws.transport.http.MessageDispatcherServlet
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition
import org.springframework.xml.xsd.SimpleXsdSchema
import org.springframework.xml.xsd.XsdSchema

@EnableWs
@Configuration
class WebServiceConfig extends WsConfigurerAdapter {
  @Bean
  ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext applicationContext) {
    MessageDispatcherServlet servlet = new MessageDispatcherServlet()
    servlet.setApplicationContext(applicationContext)
    servlet.setTransformWsdlLocations(true)
    return new ServletRegistrationBean<>(servlet, "/ws/*")
  }

  @Bean(name = "hello")
  DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema countriesSchema) {
    DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition()
    wsdl11Definition.setPortTypeName("HelloPort")
    wsdl11Definition.setLocationUri("/ws")
    wsdl11Definition.setTargetNamespace("http://opentelemetry.io/test/hello-web-service")
    wsdl11Definition.setSchema(countriesSchema)
    return wsdl11Definition
  }

  @Bean
  XsdSchema helloSchema() {
    return new SimpleXsdSchema(new ClassPathResource("hello.xsd"))
  }

}
