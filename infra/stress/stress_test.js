import http from "k6/http";
import { check, sleep } from "k6";

export let options = {
  scenarios: {
    spike_15000_at_once: {
      executor: "per-vu-iterations",
      vus: 1000, // 15,000 VU
      iterations: 1, // 각 VU당 1회 실행
      maxDuration: "1m", // VU 할당 후 1분 내에 완료
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"], // 실패 비율 <1%
  },
};

export default function () {
  const url = "http://localhost:8080/api/logs/gps";

  const payload = JSON.stringify({
    mdn: "3322698853",
    tid: "A001",
    mid: 6,
    pv: 5,
    did: 1,
    oTime: "202506171525",
    cCnt: "60",
    cList: [
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
      { sec: 1, gcd: "A", lat: 1, lon: 1, ang: 1, spd: 1, sum: 1, bat: 1 },
    ],
  });
  const params = {
    headers: {
      "Content-Type": "application/json",
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    "status is 200": (r) => r.status === 200,
  });
}
