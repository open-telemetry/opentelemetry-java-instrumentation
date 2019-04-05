plugins {
  groovy
  `java-gradle-plugin`
}

gradlePlugin {
  plugins {
    create("muzzle-plugin") {
      id = "muzzle"
      implementationClass = "MuzzlePlugin"
    }
  }
}

repositories {
  jcenter()
}

dependencies {
  compile(gradleApi())
  compile(localGroovy())

  compile("org.eclipse.aether", "aether-connector-basic", "1.1.0")
  compile("org.eclipse.aether", "aether-transport-http", "1.1.0")
  compile("org.apache.maven", "maven-aether-provider", "3.3.9")

  compile("com.google.guava", "guava", "20.0")
  compile("org.ow2.asm", "asm", "7.0-beta")
  compile("org.ow2.asm", "asm-tree", "7.0-beta")
}
