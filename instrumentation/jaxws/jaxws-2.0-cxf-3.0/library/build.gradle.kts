plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")
  compileOnly("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")

  testImplementation("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")
}
