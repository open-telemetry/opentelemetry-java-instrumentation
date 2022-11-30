plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-webmvc")
    versions.set("[3.1.0.RELEASE,6)")
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
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("org.springframework:spring-webmvc:3.1.0.RELEASE")
  compileOnly("javax.servlet:javax.servlet-api:3.1.0")
//  compileOnly("org.springframework:spring-webmvc:2.5.6")
//  compileOnly("javax.servlet:servlet-api:2.4")

  // Include servlet instrumentation for verifying the tomcat requests
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-web:spring-web-3.1:javaagent"))

  testImplementation("javax.validation:validation-api:1.1.0.Final")
  testImplementation("org.hibernate:hibernate-validator:5.4.2.Final")

  testImplementation("org.spockframework:spock-spring")

  testLibrary("org.springframework.boot:spring-boot-starter-test:1.5.17.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-web:1.5.17.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-security:1.5.17.RELEASE")

  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:2.+")
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-web:2.+")
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-security:2.+")

  testImplementation("org.springframework.security.oauth:spring-security-oauth2:2.0.16.RELEASE")

  // For spring security
  testImplementation("jakarta.xml.bind:jakarta.xml.bind-api:2.3.2")
  testImplementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.spring-webmvc.experimental-span-attributes=true")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
