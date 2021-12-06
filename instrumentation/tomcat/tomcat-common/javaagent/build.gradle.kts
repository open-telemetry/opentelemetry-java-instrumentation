plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  compileOnly(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("org.apache.tomcat.embed:tomcat-embed-core:7.0.4")
}
