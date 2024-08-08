plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
  compileOnly("org.apache.cxf:cxf-rt-frontend-jaxws:4.0.0")

  implementation(project(":instrumentation:jaxws:jaxws-3.0-cxf-4.0:javaagent"))

  testImplementation(project(":instrumentation-api"))
  testImplementation("org.apache.cxf:cxf-rt-frontend-jaxws:4.0.0")
}
