plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass{
    group.set("org.springframework")
    module.set("spring-context")
    versions.set("[5.0.4.RELEASE,6.0.0)")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:rmi:bootstrap"))
  testInstrumentation(project(":instrumentation:rmi:javaagent"))

  library("org.springframework:spring-context:5.2.9.RELEASE")
  library("org.springframework:spring-aop:5.2.9.RELEASE")
  testLibrary("org.springframework.boot:spring-boot:2.3.4.RELEASE")
}
