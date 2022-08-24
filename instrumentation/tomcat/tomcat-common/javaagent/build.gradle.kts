plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation("org.json:json:20220320")

  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  compileOnly(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("org.apache.tomcat.embed:tomcat-embed-core:7.0.4")
}
