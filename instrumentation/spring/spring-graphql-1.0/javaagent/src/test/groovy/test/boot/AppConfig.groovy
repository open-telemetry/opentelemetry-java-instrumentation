package test.boot

import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

import java.util.concurrent.Executors

@SpringBootApplication
class AppConfig implements WebMvcConfigurer {

  @Bean
  BeanPostProcessor executorAnnotatedControllerConfigurer() {
    return new BeanPostProcessor() {

      @Override
      Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof AnnotatedControllerConfigurer)) {
          return bean
        }

        ((AnnotatedControllerConfigurer) bean).setExecutor(Executors.newSingleThreadExecutor())
        return bean
      }

    }
  }

}
