plugins {
  id("com.github.bjornvester.xjc")
  id("otel.java-conventions")
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    // exclude generated web service classes
    exclude("**/hello_web_service/**")
  }
}

xjc {
  xsdDir.set(layout.projectDirectory.dir("src/main/schema"))
  useJakarta.set(true)
}

dependencies {
  api("jakarta.xml.ws:jakarta.xml.ws-api:3.0.0")
  api("jakarta.jws:jakarta.jws-api:3.0.0")

  api("org.eclipse.jetty:jetty-webapp:11.0.17")
  api("org.springframework.ws:spring-ws-core:4.0.0")

  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
}
