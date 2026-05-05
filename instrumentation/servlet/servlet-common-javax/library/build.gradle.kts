plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:servlet:servlet-common:library"))

  compileOnly("javax.servlet:servlet-api:2.3")
}
