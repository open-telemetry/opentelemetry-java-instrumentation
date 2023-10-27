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
  api("jakarta.xml.ws:jakarta.xml.ws-api:3.0.0")
  api("jakarta.jws:jakarta.jws-api:3.0.0")

  api("org.eclipse.jetty:jetty-webapp:11.0.17")
  api("org.springframework.ws:spring-ws-core:4.0.0")

  implementation(project(":testing-common"))

  xjcTool("com.sun.xml.bind:jaxb-xjc:3.0.2")
  xjcTool("com.sun.xml.bind:jaxb-impl:3.0.2")
}
