allprojects {
  repositories {
    mavenLocal()
    removeIf { it.name == "sonatype" }
  }
}
