import json
import time
import threading
import urllib.request

URL = "http://localhost:8081/api/logs"
TOTAL = 5000
THREADS = 50
PER = TOTAL // THREADS

latencies = []
errors = []
lock = threading.Lock()

def worker(tid):
    for i in range(PER):
        payload = {
            "serviceName": "order-service",
            "level": "INFO",
            "message": f"压测日志-线程{tid}-序号{i}-MQ削峰链路验证",
            "traceId": f"load{tid:03d}{i:04d}",
            "timestamp": int(time.time() * 1000),
            "tags": {"env": "perf-test"}
        }
        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(URL, data=body, headers={"Content-Type": "application/json; charset=utf-8"})
        t0 = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                code = r.status
            lat = (time.perf_counter() - t0) * 1000
            with lock:
                latencies.append(lat)
                if code != 200:
                    errors.append((tid, i, code))
        except Exception as e:
            with lock:
                errors.append((tid, i, str(e)[:80]))

start = time.perf_counter()
threads = [threading.Thread(target=worker, args=(t,)) for t in range(THREADS)]
for t in threads:
    t.start()
for t in threads:
    t.join()
elapsed = time.perf_counter() - start

total = TOTAL
qps = total / elapsed
ok = total - len(errors)
latencies.sort()
p50 = latencies[len(latencies) // 2]
p99 = latencies[int(len(latencies) * 0.99)]
print(f"总请求: {total} | 成功: {ok} | 失败: {len(errors)}")
print(f"总耗时: {elapsed:.2f}s | QPS: {qps:.0f} req/s")
print(f"延迟 P50: {p50:.1f}ms | P99: {p99:.1f}ms | Max: {latencies[-1]:.1f}ms")
if errors:
    print("前5个错误:", errors[:5])
