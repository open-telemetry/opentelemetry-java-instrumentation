val stableVersion = "1.32.1-adot2"
val alphaVersion = "1.32.1-adot2-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
