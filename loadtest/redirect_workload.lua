-- Load test for GET /{code}, the hot read/redirect path.
-- Reads pre-seeded codes from loadtest/codes.txt (one per line, generated
-- by loadtest/seed.sh) and cycles through them per worker thread.
--
-- Usage: wrk -t8 -c400 -d30s -s loadtest/redirect_workload.lua http://localhost:8080

local codes = {}

function load_codes()
    for line in io.lines("loadtest/codes.txt") do
        if #line > 0 then
            table.insert(codes, line)
        end
    end
    if #codes == 0 then
        error("codes.txt is empty - run loadtest/seed.sh first")
    end
end

load_codes()

local counter = 0

request = function()
    counter = counter + 1
    local code = codes[(counter % #codes) + 1]
    return wrk.format("GET", "/" .. code)
end

done = function(summary, latency, requests)
    io.write("------------------------------\n")
    io.write(string.format("Requests: %d, Errors: %d, Timeouts: %d\n",
        summary.requests, summary.errors.status + summary.errors.connect + summary.errors.read + summary.errors.write, summary.errors.timeout))
    io.write(string.format("p50=%.2fms p90=%.2fms p99=%.2fms max=%.2fms\n",
        latency:percentile(50) / 1000,
        latency:percentile(90) / 1000,
        latency:percentile(99) / 1000,
        latency.max / 1000))
end
