plugins {
  id("otel.javaagent-instrumentation")
}

// liberty and liberty-dispatcher are loaded into different class loaders
// liberty module has access to servlet api while liberty-dispatcher does not

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  implementation(project(":instrumentation:servlet:servlet-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
}
