plugins {
  id("org.unbroken-dome.xjc")
  id("otel.java-conventions")
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    // exclude generated web service classes
    exclude("**/hello_web_service/**")
  }
}

dependencies {
  api("javax.xml.ws:jaxws-api:2.0")
  api("javax.jws:javax.jws-api:1.1")

  api("org.eclipse.jetty:jetty-webapp:9.4.35.v20201120")
  api("org.springframework.ws:spring-ws-core:3.0.0.RELEASE")

  implementation(project(":testing-common"))
}
