plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("org.springframework:spring-webmvc:3.1.0.RELEASE")
}