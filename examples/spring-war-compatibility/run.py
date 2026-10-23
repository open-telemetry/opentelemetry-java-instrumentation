# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

"""Run released-agent comparisons without installing a host JDK or Maven."""

import argparse
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request
import uuid


HERE = Path(__file__).resolve().parent
HELLO = "hello from Spring MVC\n"
AGENTS = ("none", "1.33.0", "2.10.0", "2.10.0-indy", "2.33.0")


def docker(*args, check=True):
    completed = subprocess.run(
        ["docker", *args],
        check=False,
        text=True,
        encoding="utf-8",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    if check and completed.returncode:
        raise RuntimeError(
            f"docker {args} exited {completed.returncode}\n"
            f"{completed.stdout}\n{completed.stderr}"
        )
    return completed


def classify(log, status, body, agent, origins):
    # Require the error and the affected type in the same exception message.
    target = re.search(
        r"(?:java\.lang\.)?LinkageError:[^\r\n]*loader constraint violation"
        r"[^\r\n]*org[/\.]springframework[/\.]http[/\.]server[/\.]RequestPath",
        log,
        re.IGNORECASE,
    )
    if target:
        if all(
            marker in log
            for marker in (
                "OpenTelemetryHandlerMappingFilter.parseAndCacheMh",
                "OpenTelemetryHandlerMappingFilter.<clinit>",
                "otelAutoDispatcherFilter",
                "ContextLoaderListener.contextInitialized",
            )
        ):
            return "requestpath-loader-constraint"
        return "requestpath-loader-constraint-other-site"
    if status != 200 or body != HELLO:
        return "other-startup-or-http-failure"
    if "Context initialization failed" in log or "SEVERE" in log:
        return "other-startup-or-http-failure"
    expected = "false" if agent == "none" else "true"
    if f"otelAutoDispatcherFilter={expected}\n" not in origins:
        return "diagnostics-or-instrumentation-failure"
    version_banner = f"opentelemetry-javaagent - version: {agent.removesuffix('-indy')}"
    if agent != "none" and version_banner not in log:
        return "diagnostics-or-instrumentation-failure"
    return "healthy"


def get(url):
    try:
        with urllib.request.urlopen(url, timeout=3) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except (OSError, http.client.HTTPException) as e:
        return 0, f"{type(e).__name__}: {e}\n"


def wait_for_hello(name, base, timeout):
    deadline = time.monotonic() + timeout
    status, body = 0, ""
    while time.monotonic() < deadline:
        status, body = get(base + "/hello")
        if status == 200 or status >= 500:
            break
        logs = docker("logs", name)
        if "Context initialization failed" in logs.stdout + logs.stderr:
            break
        state = json.loads(docker("inspect", name).stdout)[0]["State"]
        if not state["Running"]:
            break
        time.sleep(1)
    return status, body


def run_case(image, image_id, agent, output, timeout, shared_after):
    name = f"spring-war-probe-{uuid.uuid4().hex[:12]}"
    output.mkdir(parents=True, exist_ok=False)
    options = "-verbose:class"
    if agent != "none":
        version = agent.removesuffix("-indy")
        options += f" -javaagent:/opt/agents/{version}.jar"
        if agent.endswith("-indy"):
            options += " -Dotel.javaagent.experimental.indy=true"
    command = [
        "run",
        "--detach",
        "--name",
        name,
        "--publish",
        "127.0.0.1::8080",
        "--env",
        f"CATALINA_OPTS={options}",
        image_id,
    ]
    (output / "command.json").write_text(json.dumps(["docker", *command], indent=2) + "\n")
    try:
        started = docker(*command)
        (output / "container.txt").write_text(started.stdout)
        port = docker("port", name, "8080/tcp").stdout.strip()
        base = f"http://{port}/probe"
        if shared_after:
            first_status, first_body = wait_for_hello(name, base, timeout)
            (output / "first-hello.txt").write_text(first_body, encoding="utf-8")
            if first_status != 200 or first_body != HELLO:
                raise RuntimeError(f"First WAR failed: HTTP {first_status}: {first_body}")
            docker("exec", name, "cp", "/opt/shared.war", "/usr/local/tomcat/webapps/shared.war")
            base = f"http://{port}/shared"
        status, body = wait_for_hello(name, base, timeout)
        startup = docker("logs", name)
        startup_log = startup.stdout + "\n" + startup.stderr
        (output / "startup.log").write_text(startup_log, encoding="utf-8")
        origin_status, origins = get(base + "/origins") if status == 200 else (0, "not requested\n")
        if shared_after:
            first_origin_status, first_origins = get(f"http://{port}/probe/origins")
            (output / "first-origins.txt").write_text(first_origins, encoding="utf-8")
            if first_origin_status != 200:
                raise RuntimeError(f"First WAR diagnostics failed: HTTP {first_origin_status}")
        (output / "hello.txt").write_text(body, encoding="utf-8")
        (output / "origins.txt").write_text(origins, encoding="utf-8")
        inspection = json.loads(docker("inspect", name).stdout)[0]
        (output / "inspect.json").write_text(json.dumps(inspection, indent=2) + "\n")
        result = {
            "image": image,
            "image_id": inspection["Image"],
            "agent": agent,
            "http_status": status,
            "origins_status": origin_status,
            "first_http_status": first_status if shared_after else None,
            "shared_after": shared_after,
            "result": classify(
                startup_log, status, body, agent, origins if origin_status == 200 else ""
            ),
        }
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result), flush=True)
        return result
    finally:
        logs = docker("logs", name, check=False)
        if logs.returncode == 0:
            (output / "tomcat.log").write_text(logs.stdout + "\n" + logs.stderr, encoding="utf-8")
        removed = docker("rm", "--force", name, check=False)
        if removed.returncode and "No such container" not in removed.stderr:
            raise RuntimeError(removed.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default="spring-war-probe:war")
    parser.add_argument("--agents", nargs="+", choices=AGENTS, default=AGENTS)
    parser.add_argument(
        "--output", type=Path, default=HERE / "results" / time.strftime("%Y%m%d-%H%M%S")
    )
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument(
        "--shared-after",
        action="store_true",
        help="Deploy a common-loader MVC WAR after the first WAR is serving requests",
    )
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    image_id = json.loads(docker("image", "inspect", args.image).stdout)[0]["Id"]
    results = [
        run_case(
            args.image, image_id, agent, args.output / agent, args.timeout, args.shared_after
        )
        for agent in args.agents
    ]
    # A reproduction is an observation, not a passing deployment.
    return 0 if all(result["result"] == "healthy" for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
