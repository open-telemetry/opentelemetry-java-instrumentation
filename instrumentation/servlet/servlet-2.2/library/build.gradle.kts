plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.slf4j:slf4j-api")

  api(project(":instrumentation:servlet:servlet-javax-common:library"))

  compileOnly("javax.servlet:servlet-api:2.2")
  compileOnly(project(":javaagent-instrumentation-api"))
}
