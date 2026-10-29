import io.opentelemetry.instrumentation.gradle.registerHttpServerBaggageTestExecutions
import org.gradle.testing.base.TestingExtension

plugins {
  id("otel.java-conventions")
}

val instrumentationProjectTest = tasks.named("test")

// batching up the muzzle tasks alphabetically into 4 chunks
// to split them up into separate CI jobs (but not too many CI job)
val instrumentationProjectMuzzle = listOf(
  tasks.register("muzzle1"),
  tasks.register("muzzle2"),
  tasks.register("muzzle3"),
  tasks.register("muzzle4"),
)

val httpServerBaggageTestSuites = mapOf(
  ":instrumentation:activej-http-6.0:javaagent" to listOf("test"),
  ":instrumentation:armeria:armeria-1.3:javaagent" to listOf("test", "testArmeria19"),
  ":instrumentation:armeria:armeria-1.3:library" to listOf("test"),
  ":instrumentation:dropwizard:dropwizard-testing" to listOf("test"),
  ":instrumentation:finagle-http-23.11:javaagent" to listOf("test"),
  ":instrumentation:grails-3.0:javaagent" to listOf("test"),
  ":instrumentation:grizzly-2.3:javaagent" to listOf("test"),
  ":instrumentation:helidon-4.3:javaagent" to listOf("test"),
  ":instrumentation:helidon-4.3:library" to listOf("test"),
  ":instrumentation:java-http-server:javaagent" to listOf("test"),
  ":instrumentation:java-http-server:library" to listOf("test"),
  ":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-cxf-3.2:javaagent" to listOf("test"),
  ":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-jersey-2.0:javaagent" to listOf("test"),
  ":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-3.0:javaagent" to listOf("test"),
  ":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-3.1:javaagent" to listOf("test"),
  ":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-jersey-3.0:javaagent" to listOf("test"),
  ":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-resteasy-6.0:javaagent" to listOf("test"),
  ":instrumentation:jetty:jetty-11.0:javaagent" to listOf("test"),
  ":instrumentation:jetty:jetty-12.0:javaagent" to listOf("test"),
  ":instrumentation:jetty:jetty-8.0:javaagent" to listOf("test"),
  ":instrumentation:jfinal-3.2:javaagent" to listOf("test"),
  ":instrumentation:netty:netty-3.8:javaagent" to listOf("test"),
  ":instrumentation:netty:netty-4.0:javaagent" to listOf("test"),
  ":instrumentation:netty:netty-4.1:javaagent" to listOf("test"),
  ":instrumentation:netty:netty-4.1:library" to listOf("test"),
  ":instrumentation:play:play-mvc:play-mvc-2.4:javaagent" to listOf("test", "play24Test"),
  ":instrumentation:play:play-mvc:play-mvc-2.6:javaagent" to listOf("test", "play28Test", "play3Test"),
  ":instrumentation:ratpack:ratpack-1.4:javaagent" to listOf("test", "version17Test"),
  ":instrumentation:ratpack:ratpack-1.7:library" to listOf("test"),
  ":instrumentation:restlet:restlet-1.1:javaagent" to listOf("test"),
  ":instrumentation:restlet:restlet-1.1:library" to listOf("test"),
  ":instrumentation:restlet:restlet-2.0:javaagent" to listOf("test"),
  ":instrumentation:restlet:restlet-2.0:library" to listOf("test"),
  ":instrumentation:servlet:servlet-2.2:javaagent" to listOf("test"),
  ":instrumentation:servlet:servlet-3.0:javaagent-testing" to listOf("test"),
  ":instrumentation:servlet:servlet-3.0:library" to listOf("test"),
  ":instrumentation:servlet:servlet-5.0:jetty11-testing" to listOf("test"),
  ":instrumentation:servlet:servlet-5.0:jetty12-testing" to listOf("test"),
  ":instrumentation:servlet:servlet-5.0:library" to listOf("test"),
  ":instrumentation:servlet:servlet-5.0:tomcat-testing" to listOf("test"),
  ":instrumentation:spring:spring-webflux:spring-webflux-5.0:javaagent" to listOf("test"),
  ":instrumentation:spring:spring-webflux:spring-webflux-5.0:testing-webflux7" to listOf("test"),
  ":instrumentation:spring:spring-webflux:spring-webflux-5.3:library" to listOf("test"),
  ":instrumentation:spring:spring-webmvc:spring-webmvc-3.1:javaagent" to listOf("test"),
  ":instrumentation:spring:spring-webmvc:spring-webmvc-5.3:library" to listOf("test"),
  ":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:javaagent" to listOf("test"),
  ":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library" to listOf("test"),
  ":instrumentation:struts:struts-2.3:javaagent" to listOf("test"),
  ":instrumentation:struts:struts-7.0:javaagent" to listOf("test"),
  ":instrumentation:tomcat:tomcat-10.0:javaagent" to listOf("test"),
  ":instrumentation:tomcat:tomcat-7.0:javaagent" to listOf("test"),
  ":instrumentation:undertow-1.4:javaagent" to listOf("test"),
  ":instrumentation:vertx:vertx-rx-java-3.5:javaagent" to
    listOf("version35Test", "version41Test", "version5Test"),
  ":instrumentation:vertx:vertx-web-3.0:javaagent" to
    listOf("version3Test", "version41Test", "version45Test", "version5Test")
)

var counter = 0
subprojects {
  val subProj = this
  httpServerBaggageTestSuites[path]?.let { suiteNames ->
    pluginManager.withPlugin("otel.java-conventions") {
      subProj.afterEvaluate {
        extensions.getByType(TestingExtension::class.java)
          .registerHttpServerBaggageTestExecutions(suiteNames)
      }
    }
  }

  plugins.withId("java") {
    instrumentationProjectTest.configure {
      dependsOn(subProj.tasks.named("test"))
    }
  }

  plugins.withId("io.opentelemetry.instrumentation.muzzle-check") {
    // relying on predictable ordering of subprojects
    // (see https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#N14CB4)
    // since we are splitting these muzzleX tasks across different github action jobs
    instrumentationProjectMuzzle[counter++ % 4].get().dependsOn(subProj.tasks.named("muzzle"))
  }
}
