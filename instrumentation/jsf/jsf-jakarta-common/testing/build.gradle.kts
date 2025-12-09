plugins {
  id("otel.java-conventions")
}

dependencies {
  api("ch.qos.logback:logback-classic")
  api("org.slf4j:log4j-over-slf4j")
  api("org.slf4j:jcl-over-slf4j")
  api("org.slf4j:jul-to-slf4j")

  compileOnly("jakarta.faces:jakarta.faces-api:3.0.0")
  compileOnly("jakarta.el:jakarta.el-api:4.0.0")

  api("org.eclipse.jetty:jetty-webapp:11.0.0")
  api("org.eclipse.jetty:apache-jstl:11.0.0")
  api("org.eclipse.jetty:apache-jsp:11.0.0")

  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("org.jsoup:jsoup:1.13.1")

  implementation("org.glassfish:jakarta.el:4.0.2")
  implementation("jakarta.websocket:jakarta.websocket-api:2.0.0")
}
