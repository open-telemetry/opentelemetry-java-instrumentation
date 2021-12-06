plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.springframework:spring-webmvc:3.1.0.RELEASE")
  compileOnly("javax.servlet:javax.servlet-api:3.1.0")
}
