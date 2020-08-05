plugins {
  groovy
  `java-gradle-plugin`
  id("com.diffplug.gradle.spotless") version "4.3.0"
}

gradlePlugin {
  plugins {
    create("muzzle-plugin") {
      id = "muzzle"
      implementationClass = "MuzzlePlugin"
    }
    create("auto-instrumentation-plugin") {
      id = "io.opentelemetry.instrumentation.auto-instrumentation"
      implementationClass = "io.opentelemetry.instrumentation.gradle.AutoInstrumentationPlugin"
    }
  }
}

repositories {
  mavenLocal()
  jcenter()
  mavenCentral()
}

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())

  implementation("org.eclipse.aether", "aether-connector-basic", "1.1.0")
  implementation("org.eclipse.aether", "aether-transport-http", "1.1.0")
  implementation("org.apache.maven", "maven-aether-provider", "3.3.9")

  implementation("com.google.guava", "guava", "20.0")
  implementation("org.ow2.asm", "asm", "7.0-beta")
  implementation("org.ow2.asm", "asm-tree", "7.0-beta")
  implementation("org.apache.httpcomponents:httpclient:4.5.10")

  testImplementation("org.spockframework", "spock-core", "1.3-groovy-2.5")
  testImplementation("org.codehaus.groovy", "groovy-all", "2.5.8")
}
