#!/usr/bin/env python3
"""Identify the most-flaky JUnit test from Develocity over a recent window.

Uses the dashboard's internal data endpoints (the same ones the SPA fetches)
since the public ``/api/builds`` endpoint requires an access key with the
"Access build data via the API" scope, which the org's
``DEVELOCITY_ACCESS_KEY`` does not have. The dashboard endpoints are
unauthenticated and return JSON.

Reads the skip list from ``build/flaky-test-remediation/skip.txt`` (one Develocity
test container/class name per line) and writes ``build/flaky-test-remediation/selected.json``.
"""

import json
import os
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from _paths import OUT_DIR, SELECTED, SKIP

DEFAULT_DEVELOCITY_URL = "https://develocity.opentelemetry.io"
PROJECT_NAME = "opentelemetry-java-instrumentation"
WORKSPACE_ROOT = Path(subprocess.check_output(
    ["git", "rev-parse", "--show-toplevel"], text=True).strip())
SOURCE_EXTS = (".java", ".groovy", ".kt")

WINDOW_DAYS = 7
MIN_FLAKY = 5
RECENT_MODIFY_DAYS = 7


def http_get_json(url, *, timeout=60):
    headers = {
        "Accept": "application/json",
        "User-Agent": "otel-flaky-test-remediation/1.0",
    }
    with urlopen(Request(url, headers=headers), timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def common_query(*, since_ms, until_ms):
    return {
        "rootProjectNames": PROJECT_NAME,
        "startTimeMin": str(since_ms),
        "startTimeMax": str(until_ms),
        "timeZoneId": "UTC",
    }


def total_flaky(test):
    """Sum the per-bucket flaky counts in an outcomeTrend entry."""
    points = (test.get("outcomeTrend") or {}).get("dataPoints") or []
    return sum(int((p.get("outcomeDistribution") or {}).get("flaky") or 0)
               for p in points)


def fetch_top_tests(base, *, since_ms, until_ms):
    params = common_query(since_ms=since_ms, until_ms=until_ms)
    params["sortField"] = "FLAKY"
    payload = http_get_json(f"{base}/tests-data/top?{urlencode(params)}")
    return ((payload.get("data") or {}).get("topTests") or {}).get("tests") or []


def fetch_container_methods(base, *, container, since_ms, until_ms):
    params = common_query(since_ms=since_ms, until_ms=until_ms)
    params["container"] = container
    params["sortField"] = "FLAKY"
    payload = http_get_json(
        f"{base}/tests-data/test-container-history?{urlencode(params)}")
    single = (payload.get("data") or {}).get("singleContainerDetails") or {}
    return (single.get("tests") or {}).get("tests") or []


def fetch_test_history(base, *, container, test_name, since_ms, until_ms):
    params = common_query(since_ms=since_ms, until_ms=until_ms)
    params["container"] = container
    params["test"] = test_name
    payload = http_get_json(
        f"{base}/tests-data/test-history?{urlencode(params)}")
    return payload.get("data") or {}


def flaky_day_buckets(history):
    """Day-buckets [(start_ms, end_ms)] in outcomeTrend that contain at
    least one flaky/failed execution, most-recent-first."""
    points = ((history.get("outcomeTrend") or {}).get("dataPoints")) or []
    buckets = []
    for p in points:
        dist = p.get("outcomeDistribution") or {}
        if (dist.get("flaky") or 0) > 0 or (dist.get("failed") or 0) > 0:
            s, e = p.get("startTimestamp"), p.get("endTimestamp")
            if isinstance(s, int) and isinstance(e, int):
                buckets.append((s, e))
    buckets.sort(key=lambda be: be[0], reverse=True)
    return buckets


def _all_source_files():
    """Tracked .java/.groovy/.kt files, posix-relative to WORKSPACE_ROOT."""
    out = subprocess.check_output(
        ["git", "-C", str(WORKSPACE_ROOT), "ls-files",
         *(f"*{ext}" for ext in SOURCE_EXTS)],
        text=True,
    )
    return out.splitlines()


def find_test_source(class_fqcn, *, all_files):
    """Locate the source file for a fully-qualified class name.
    Disambiguates simple-name collisions by matching the package on the
    file's directory path."""
    outer = class_fqcn.split("$", 1)[0]
    simple = outer.rsplit(".", 1)[-1]
    package = outer.rsplit(".", 1)[0] if "." in outer else ""
    package_path = "/" + package.replace(".", "/") + "/" if package else ""
    targets = {f"{simple}{ext}" for ext in SOURCE_EXTS}
    candidates = [f for f in all_files if f.rsplit("/", 1)[-1] in targets]
    if not candidates:
        return None
    if package_path:
        for hit in candidates:
            if package_path in f"/{hit}":
                return WORKSPACE_ROOT / hit
    return WORKSPACE_ROOT / candidates[0]


def recently_modified(path, *, days):
    rel = path.relative_to(WORKSPACE_ROOT).as_posix()
    result = subprocess.run(
        ["git", "log", "-1", f"--since={days}.days.ago", "--format=%H",
         "--", rel],
        cwd=WORKSPACE_ROOT, capture_output=True, text=True, check=False,
    )
    return bool(result.stdout.strip())


def best_failure_sample(history):
    """Return (build_id, failure_message) from the most recent test result
    that carries a non-null ``firstFailureMessage``."""
    for r in (history.get("testResults") or []):
        msg = r.get("firstFailureMessage")
        if isinstance(msg, str) and msg.strip():
            return r.get("buildId") or "", msg
    return "", ""


def collect_flaky_scans(history, *, base, limit):
    """Up to ``limit`` recent scans where the test failed or flaked."""
    out = []
    seen = set()
    for r in (history.get("testResults") or []):
        if r.get("outcome") not in ("flaky", "failed"):
            continue
        bid = r.get("buildId") or ""
        if not bid or bid in seen:
            continue
        seen.add(bid)
        msg = r.get("firstFailureMessage") or ""
        out.append({
            "build_id": bid,
            "scan_url": f"{base}/s/{bid}",
            "outcome": r.get("outcome") or "",
            "timestamp_ms": r.get("startTimestamp") or 0,
            "tags": r.get("tags") or [],
            "work_unit": r.get("workUnitName") or "",
            "failure_excerpt": (msg[:600] + (" \u2026" if len(msg) > 600 else ""))
                                if msg else "",
        })
        if len(out) >= limit:
            break
    return out


def per_day_flake_breakdown(history):
    points = ((history.get("outcomeTrend") or {}).get("dataPoints")) or []
    rows = []
    for p in points:
        dist = p.get("outcomeDistribution") or {}
        flaky = dist.get("flaky") or 0
        failed = dist.get("failed") or 0
        passed = dist.get("passed") or 0
        if flaky or failed or passed:
            rows.append({
                "start_ms": p.get("startTimestamp") or 0,
                "end_ms": p.get("endTimestamp") or 0,
                "flaky": flaky,
                "failed": failed,
                "passed": passed,
            })
    return rows


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    SELECTED.unlink(missing_ok=True)

    base = os.environ.get("DEVELOCITY_URL", DEFAULT_DEVELOCITY_URL).rstrip("/")

    skip = set()
    if SKIP.exists():
        skip = {
            line.strip()
            for line in SKIP.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")
        }

    until_ms = int(time.time() * 1000)
    since_ms = until_ms - WINDOW_DAYS * 86400 * 1000
    print(f"Querying Develocity {base} for window "
          f"[{since_ms}..{until_ms}] ({WINDOW_DAYS}d).")

    top = fetch_top_tests(base, since_ms=since_ms, until_ms=until_ms)
    print(f"Top containers returned: {len(top)}")
    all_files = _all_source_files()

    selected = None
    for container in top:
        cname = container.get("name") or ""
        if not cname:
            continue
        c_flaky = total_flaky(container)
        if c_flaky < MIN_FLAKY:
            print(f"info: container {cname} below threshold "
                  f"({c_flaky} < {MIN_FLAKY})")
            break  # list is sorted; nothing past this will qualify

        outer = cname.split("$", 1)[0]
        source = find_test_source(outer, all_files=all_files)
        if source is None:
            print(f"info: skipping {cname}: source not found")
            continue
        if recently_modified(source, days=RECENT_MODIFY_DAYS):
            print(f"info: skipping {cname}: source modified in last "
                  f"{RECENT_MODIFY_DAYS}d")
            continue

        methods = fetch_container_methods(
            base, container=cname,
            since_ms=since_ms, until_ms=until_ms,
        )
        ranked = sorted(
            ({"name": m.get("name") or "", "flaky": total_flaky(m)}
             for m in methods),
            key=lambda m: m["flaky"], reverse=True,
        )
        ranked = [m for m in ranked if m["flaky"] >= MIN_FLAKY and m["name"]]
        if not ranked:
            print(f"info: no per-method flake data for {cname}")
            continue

        chosen = None
        for m in ranked:
            fq = f"{cname}.{m['name']}"
            if fq in skip or cname in skip:
                print(f"info: skipping {fq} (on skip list)")
                continue
            chosen = m
            break
        if chosen is None:
            continue

        history = fetch_test_history(
            base, container=cname, test_name=chosen["name"],
            since_ms=since_ms, until_ms=until_ms,
        )
        sample_build, sample_failure = best_failure_sample(history)
        recent_scans = collect_flaky_scans(history, base=base, limit=5)
        # The window-wide /tests-data/test-history response truncates
        # results and tends to omit the flaky/failed entries. Re-query
        # narrowed to each day-bucket that had a flaky execution to fill
        # in the failure sample and recent-scan list.
        if not sample_failure or not recent_scans:
            for s_ms, e_ms in flaky_day_buckets(history):
                day_history = fetch_test_history(
                    base, container=cname, test_name=chosen["name"],
                    since_ms=s_ms, until_ms=e_ms,
                )
                if not sample_failure:
                    sample_build, sample_failure = best_failure_sample(
                        day_history)
                day_scans = collect_flaky_scans(
                    day_history, base=base, limit=5 - len(recent_scans),
                )
                seen = {s["build_id"] for s in recent_scans}
                for s in day_scans:
                    if s["build_id"] not in seen:
                        recent_scans.append(s)
                        seen.add(s["build_id"])
                if sample_failure and len(recent_scans) >= 5:
                    break

        method = chosen["name"]
        selected = {
            "class": cname,
            "method": method,
            "fully_qualified": f"{cname}.{method}",
            "flaky_count": chosen["flaky"],
            "container_flaky_count": c_flaky,
            "source_file": source.relative_to(WORKSPACE_ROOT).as_posix(),
            "sample_build_id": sample_build,
            "sample_scan_url": f"{base}/s/{sample_build}" if sample_build else "",
            "sample_failure": (sample_failure or "")[:8000],
            "recent_flaky_scans": recent_scans,
            "per_day_breakdown": per_day_flake_breakdown(history),
            "window_days": WINDOW_DAYS,
            "develocity_url": base,
        }
        break

    if selected is None:
        print("No flaky test candidate found that satisfies all constraints.")
        return 0

    SELECTED.write_text(json.dumps(selected, indent=2), encoding="utf-8")
    print(f"Selected: {selected['fully_qualified']} "
          f"(flaky={selected['flaky_count']}, "
          f"source={selected['source_file']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
