val stableVersion = "3.0.0-SNAPSHOT"
val alphaVersion = "3.0.0-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.31.1"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
