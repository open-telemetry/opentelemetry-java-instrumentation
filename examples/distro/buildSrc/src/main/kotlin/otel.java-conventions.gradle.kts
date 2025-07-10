plugins {
  java
}

repositories {
  mavenLocal()
  mavenCentral()
  maven {
    name = "sonatype"
    url = uri("https://oss.sonatype.org/content/repositories/snapshots")
  }
}
evaluationDependsOn(":dependencyManagement")
val dependencyManagementConf = configurations.create("dependencyManagement") {
  isCanBeConsumed = false
  isCanBeResolved = false
  isVisible = false
}
afterEvaluate {
  configurations.configureEach {
    if (isCanBeResolved && !isCanBeConsumed) {
      extendsFrom(dependencyManagementConf)
    }
  }
}

dependencies {
  add(dependencyManagementConf.name, platform(project(":dependencyManagement")))
  add("testImplementation", "org.mockito:mockito-core")
  add("testImplementation", enforcedPlatform("org.junit:junit-bom"))
  add("testImplementation", "org.junit.jupiter:junit-jupiter-api")
  add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine")
  add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(8)
} 
