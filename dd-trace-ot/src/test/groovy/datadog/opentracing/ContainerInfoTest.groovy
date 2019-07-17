package datadog.opentracing

import spock.lang.Specification
import spock.lang.Unroll


class ContainerInfoTest extends Specification {

  @Unroll
  def "CGroupInfo is parsed from individual lines"() {
    when:
    ContainerInfo.CGroupInfo cGroupInfo = ContainerInfo.parseLine(line)

    then:
    cGroupInfo.getId() == id
    cGroupInfo.getPath() == path
    cGroupInfo.getControllers() == controllers
    cGroupInfo.getContainerId() == containerId
    cGroupInfo.podId == podId

    // Examples from container tagging rfc and Qard/container-info
    where:
    id | controllers             | path                                                                                                                                                                                    | containerId                                                        | podId                                  | line

    // Docker examples
    13 | ["name=systemd"]        | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "13:name=systemd:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    12 | ["pids"]                | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "12:pids:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    11 | ["hugetlb"]             | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "11:hugetlb:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    10 | ["net_prio"]            | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "10:net_prio:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    9  | ["perf_event"]          | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "9:perf_event:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    8  | ["net_cls"]             | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "8:net_cls:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    7  | ["freezer"]             | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "7:freezer:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    6  | ["devices"]             | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "6:devices:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    5  | ["memory"]              | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "5:memory:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    4  | ["blkio"]               | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "4:blkio:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    3  | ["cpuacct"]             | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "3:cpuacct:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    2  | ["cpu"]                 | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "2:cpu:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"
    1  | ["cpuset"]              | "/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"                                                                                                              | "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | "1:cpuset:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"

    // Kubernates examples
    11 | ["perf_event"]          | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "11:perf_event:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    10 | ["pids"]                | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "10:pids:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    9  | ["memory"]              | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "9:memory:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    8  | ["cpu", "cpuacct"]      | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "8:cpu,cpuacct:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    7  | ["blkio"]               | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "7:blkio:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    6  | ["cpuset"]              | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "6:cpuset:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    5  | ["devices"]             | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "5:devices:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    4  | ["freezer"]             | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "4:freezer:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    3  | ["net_cls", "net_prio"] | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "3:net_cls,net_prio:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    2  | ["hugetlb"]             | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "2:hugetlb:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"
    1  | ["name=systemd"]        | "/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"                                                         | "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | "1:name=systemd:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"

    //ECS examples
    9  | ["perf_event"]          | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "9:perf_event:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    8  | ["memory"]              | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "8:memory:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    7  | ["hugetlb"]             | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "7:hugetlb:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    6  | ["freezer"]             | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "6:freezer:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    5  | ["devices"]             | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "5:devices:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    4  | ["cpuset"]              | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "4:cpuset:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    3  | ["cpuacct"]             | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "3:cpuacct:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    2  | ["cpu"]                 | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "2:cpu:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"
    1  | ["blkio"]               | "/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"                                                            | "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | "1:blkio:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"

    //Fargate Examples
    11 | ["hugetlb"]             | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "11:hugetlb:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    10 | ["pids"]                | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "10:pids:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    9  | ["cpuset"]              | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "9:cpuset:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    8  | ["net_cls", "net_prio"] | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "8:net_cls,net_prio:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    7  | ["cpu", "cpuacct"]      | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "7:cpu,cpuacct:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    6  | ["perf_event"]          | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "6:perf_event:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    5  | ["freezer"]             | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "5:freezer:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    4  | ["devices"]             | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "4:devices:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    3  | ["blkio"]               | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "3:blkio:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    2  | ["memory"]              | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "2:memory:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"
    1  | ["name=systemd"]        | "/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"                                                                            | "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | "1:name=systemd:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"

    //Reference impl examples
    1  | ["name=systemd"]        | "/system.slice/docker-cde7c2bab394630a42d73dc610b9c57415dced996106665d427f6d0566594411.scope"                                                                                           | "cde7c2bab394630a42d73dc610b9c57415dced996106665d427f6d0566594411" | null                                   | "1:name=systemd:/system.slice/docker-cde7c2bab394630a42d73dc610b9c57415dced996106665d427f6d0566594411.scope"
    1  | ["name=systemd"]        | "/docker/051e2ee0bce99116029a13df4a9e943137f19f957f38ac02d6bad96f9b700f76/not_hex"                                                                                                      | null                                                               | null                                   | "1:name=systemd:/docker/051e2ee0bce99116029a13df4a9e943137f19f957f38ac02d6bad96f9b700f76/not_hex"
    1  | ["name=systemd"]        | "/kubepods.slice/kubepods-burstable.slice/kubepods-burstable-pod90d81341_92de_11e7_8cf2_507b9d4141fa.slice/crio-2227daf62df6694645fee5df53c1f91271546a9560e8600a525690ae252b7f63.scope" | "2227daf62df6694645fee5df53c1f91271546a9560e8600a525690ae252b7f63" | "90d81341_92de_11e7_8cf2_507b9d4141fa" | "1:name=systemd:/kubepods.slice/kubepods-burstable.slice/kubepods-burstable-pod90d81341_92de_11e7_8cf2_507b9d4141fa.slice/crio-2227daf62df6694645fee5df53c1f91271546a9560e8600a525690ae252b7f63.scope"

  }

  @Unroll
  def "Container info parsed from file content"() {
    when:
    ContainerInfo containerInfo = ContainerInfo.parse(content)

    then:
    containerInfo.getContainerId() == containerId
    containerInfo.getPodId() == podId
    containerInfo.getCGroups().size() == size

    where:
    containerId                                                        | podId                                  | size | content
    // Docker
    "3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860" | null                                   | 13   | """13:name=systemd:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
12:pids:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
11:hugetlb:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
10:net_prio:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
9:perf_event:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
8:net_cls:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
7:freezer:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
6:devices:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
5:memory:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
4:blkio:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
3:cpuacct:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
2:cpu:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860
1:cpuset:/docker/3726184226f5d3147c25fdeab5b60097e378e8a720503a5e19ecfdf29f869860"""

    // Kubernetes
    "3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1" | "3d274242-8ee0-11e9-a8a6-1e68d864ef1a" | 11   | """11:perf_event:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
10:pids:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
9:memory:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
8:cpu,cpuacct:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
7:blkio:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
6:cpuset:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
5:devices:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
4:freezer:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
3:net_cls,net_prio:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
2:hugetlb:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1
1:name=systemd:/kubepods/besteffort/pod3d274242-8ee0-11e9-a8a6-1e68d864ef1a/3e74d3fd9db4c9dd921ae05c2502fb984d0cde1b36e581b13f79c639da4518a1"""

    // ECS
    "38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce" | null                                   | 9    | """9:perf_event:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
8:memory:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
7:hugetlb:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
6:freezer:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
5:devices:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
4:cpuset:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
3:cpuacct:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
2:cpu:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce
1:blkio:/ecs/haissam-ecs-classic/5a0d5ceddf6c44c1928d367a815d890f/38fac3e99302b3622be089dd41e7ccf38aff368a86cc339972075136ee2710ce"""

    // Fargate
    "432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da" | null                                   | 11   | """11:hugetlb:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
10:pids:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
9:cpuset:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
8:net_cls,net_prio:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
7:cpu,cpuacct:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
6:perf_event:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
5:freezer:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
4:devices:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
3:blkio:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
2:memory:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da
1:name=systemd:/ecs/55091c13-b8cf-4801-b527-f4601742204d/432624d2150b349fe35ba397284dea788c2bf66b885d14dfc1569b01890ca7da"""
  }
}
