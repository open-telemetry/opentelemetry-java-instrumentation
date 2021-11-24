plugins {
  `java-library`

  id("ru.vyarus.animalsniffer")
}

dependencies {
  add("signature", "com.toasttab.android:gummy-bears-api-21:0.3.0:coreLib@signature")
}

animalsniffer {
  sourceSets = listOf(java.sourceSets.main.get())
}