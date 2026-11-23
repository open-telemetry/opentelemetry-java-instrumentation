import names from "./names.js";

const originalRandom = Math.random;
try {
    for (let i = 0; i < 4096; i++) {
        Math.random = () => i / 4096;
        const vet = names.randomVet([]);
        if (!/^[a-zA-Z]+$/.test(vet.lastName)) {
            throw new Error(`Invalid vet surname generated: ${vet.lastName}`);
        }
    }
} finally {
    Math.random = originalRandom;
}

export default function() {}
