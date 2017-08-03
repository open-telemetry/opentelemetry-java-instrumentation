package com.datadoghq.trace.integration

import spock.lang.Specification

class URLAsResourceNameTest extends Specification {

  def "load the config from the yaml files"() {
    setup:
    def patterns = new URLAsResourceName("dd-url-patterns").getPatterns()

    expect:
    patterns.size() == 4
    // 0 and 1 are from the config file
    patterns.get(0).regex == ".*"
    patterns.get(0).replacement == "foo"
    patterns.get(1).regex == "foo"
    patterns.get(1).replacement == "bar"

    // the last and before-last are defaults
    patterns.get(patterns.size() - 2) == URLAsResourceName.RULE_QPARAM
    patterns.get(patterns.size() - 1) == URLAsResourceName.RULE_DIGIT

  }

  def "remove query params"() {

    setup:
    def decorator = new URLAsResourceName()

    when:
    def norm = decorator.norm(input)

    then:
    norm == output

    where:
    input << ["/search?id=100&status=true"]
    output << ["/search"]

  }

  def "should replace all digits"() {

    setup:
    def decorator = new URLAsResourceName()

    when:
    def norm = decorator.norm(input)

    then:
    norm == output

    where:
    input << ["/user/100/repository/50"]
    output << ["/user/?/repository/?"]

  }

  def "norm should apply custom rules"() {

    setup:
    def decorator = new URLAsResourceName()
    def r1 = new URLAsResourceName.Config.Rule(/(\\/users\\/)([^\/]*)/, /$1:id/)
    decorator.setPatterns(Arrays.asList(r1))

    when:
    def norm = decorator.norm(input)

    then:
    norm == output

    where:
    input << ["/users/guillaume/list_repository/"]
    output << ["/users/:id/list_repository/"]

  }

  def "skip others rules if the current is set as final"() {

    // Same test as above except we replace :id by :id_01
    // And we want to stop the rule chain just after that.

    setup:
    def decorator = new URLAsResourceName()
    def r1 = new URLAsResourceName.Config.Rule(/(\\/users\\/)([^\/]*)/, /$1:id_01/)
    decorator.setPatterns(Arrays.asList(r1, URLAsResourceName.RULE_DIGIT))

    when:
    r1.setFinal(false)
    def norm = decorator.norm(input)

    then:
    norm == "/users/:id_?/list_repository/"

    when:
    r1.setFinal(true)
    norm = decorator.norm(input)

    then:
    norm == "/users/:id_01/list_repository/"

    where:
    input = "/users/guillaume/list_repository/"

  }
}
