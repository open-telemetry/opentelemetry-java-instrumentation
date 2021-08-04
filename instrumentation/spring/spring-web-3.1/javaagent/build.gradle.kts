plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-web")
    versions.set("[3.1.0.RELEASE,]")
    // these versions depend on org.springframework:spring-web which has a bad dependency on
    // javax.faces:jsf-api:1.1 which was released as pom only
    skip("1.2.1", "1.2.2", "1.2.3", "1.2.4")
    // 3.2.1.RELEASE has transitive dependencies like spring-web as "provided" instead of "compile"
    skip("3.2.1.RELEASE")
    extraDependency("javax.servlet:javax.servlet-api:3.0.1")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.springframework:spring-web:3.1.0.RELEASE")
  compileOnly("javax.servlet:javax.servlet-api:3.1.0")
}
