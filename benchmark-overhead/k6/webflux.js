import { check } from "k6";
import exec from "k6/execution";
import http from "k6/http";
import { Gauge } from "k6/metrics";

const baseUrl = "http://webflux:8080";
const cpuCores = new Gauge("app_cpu_cores");
const rate = Number(__ENV.BENCHMARK_RATE);
const seconds = Number(__ENV.BENCHMARK_SECONDS);
const target = rate * seconds;
const seed = [
    ["George", "Franklin"],
    ["Betty", "Davis"],
    ["Eduardo", "Rodriquez"],
    ["Harold", "Davis"],
    ["Peter", "McTavish"],
    ["Jean", "Coleman"],
    ["Jeff", "Black"],
    ["Maria", "Escobito"],
    ["David", "Schroeder"],
    ["Carlos", "Estaban"],
];

export const options = {
    maxRedirects: 0,
    scenarios: {
        workload: {
            executor: "constant-arrival-rate",
            rate,
            timeUnit: "1s",
            duration: `${seconds}s`,
            preAllocatedVUs: 128,
            gracefulStop: "10s",
        },
    },
    thresholds: {
        // k6 can schedule one extra iteration at the end of the arrival window.
        "http_reqs{phase:workload}": [`count>=${target - 1}`, `count<=${target + 1}`],
        "http_req_duration{phase:workload}": ["p(95)>=0"],
        "http_req_failed{phase:workload}": ["rate==0"],
        dropped_iterations: ["count==0"],
        checks: ["rate==1"],
    },
};

function cpuSnapshot() {
    const response = http.get(`${baseUrl}/cpu`, { timeout: "5s" });
    if (response.status !== 200) {
        throw new Error(`CPU snapshot returned HTTP ${response.status}`);
    }
    const snapshot = response.json();
    if (!(snapshot.processCpuNanos > 0) || !(snapshot.uptimeMillis > 0)) {
        throw new Error(`Invalid CPU snapshot: ${response.body}`);
    }
    return snapshot;
}

export function setup() {
    return __ENV.BENCHMARK_SUMMARY ? cpuSnapshot() : null;
}

export function teardown(before) {
    if (before) {
        const after = cpuSnapshot();
        const elapsedMs = after.uptimeMillis - before.uptimeMillis;
        const cpuNanos = after.processCpuNanos - before.processCpuNanos;
        if (elapsedMs < seconds * 1000 || cpuNanos <= 0) {
            throw new Error(`Invalid CPU interval: ${elapsedMs} ms, ${cpuNanos} CPU ns`);
        }
        cpuCores.add(cpuNanos / (elapsedMs * 1e6));
    }
}

export default function () {
    const iteration = exec.scenario.iterationInTest;
    const seedIndex = iteration % seed.length;
    const id = seedIndex + 1;
    const requestId = `request-${iteration}`;
    const response = http.get(`${baseUrl}/owners/${id}`, {
        headers: { "X-Request-Id": requestId },
        tags: { name: "GET /owners/:id", phase: "workload" },
        timeout: "5s",
    });
    const body = response.status === 200 ? response.json() : null;
    check(response, {
        "seeded owner and request context": () => body?.owner?.id === id
            && body.owner.firstName === seed[seedIndex][0]
            && body.owner.lastName === seed[seedIndex][1]
            && body.requestId === requestId,
    });
}

export function handleSummary(data) {
    if (!__ENV.BENCHMARK_SUMMARY) {
        return {};
    }
    return { [__ENV.BENCHMARK_SUMMARY]: JSON.stringify(data, null, 2) };
}
