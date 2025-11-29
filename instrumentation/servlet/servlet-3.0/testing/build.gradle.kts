plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly(project(":instrumentation:servlet:servlet-common:bootstrap"))

  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("javax.servlet:javax.servlet-api:3.0.1")

  implementation("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  implementation("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")
  implementation("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  implementation("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")
}
