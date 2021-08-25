plugins {
  id("otel.java-conventions")
}

dependencies {
  api("ch.qos.logback:logback-classic")
  api("org.slf4j:log4j-over-slf4j")
  api("org.slf4j:jcl-over-slf4j")
  api("org.slf4j:jul-to-slf4j")

  compileOnly("jakarta.faces:jakarta.faces-api:2.3.2")
  compileOnly("jakarta.el:jakarta.el-api:3.0.3")

  implementation(project(":testing-common"))
  implementation("org.jsoup:jsoup:1.13.1")

  val jettyVersion = "9.4.35.v20201120"
  api("org.eclipse.jetty:jetty-annotations:$jettyVersion")
  implementation("org.eclipse.jetty:apache-jsp:$jettyVersion")
  implementation("org.glassfish:jakarta.el:3.0.2")
  implementation("jakarta.websocket:jakarta.websocket-api:1.1.1")
}
