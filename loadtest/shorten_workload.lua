-- Load test for POST /api/v1/shorten.
-- Usage: wrk -t8 -c400 -d30s -s loadtest/shorten_workload.lua http://localhost:8080

wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"

math.randomseed(os.time())

request = function()
    local n = math.random(1, 1000000000)
    local body = string.format('{"longUrl":"https://example.com/articles/%d"}', n)
    return wrk.format(nil, "/api/v1/shorten", nil, body)
end

response = function(status, headers, body)
    if status ~= 201 then
        errors = (errors or 0) + 1
    end
end

done = function(summary, latency, requests)
    io.write("------------------------------\n")
    io.write(string.format("Requests: %d, Errors: %d\n", summary.requests, errors or 0))
    io.write(string.format("p50=%.2fms p90=%.2fms p99=%.2fms max=%.2fms\n",
        latency:percentile(50) / 1000,
        latency:percentile(90) / 1000,
        latency:percentile(99) / 1000,
        latency.max / 1000))
end
