import http from "k6/http";
import { check, sleep } from "k6";

export let options = {
  scenarios: {
    random_minute_load: {
      executor: "per-vu-iterations",
      vus: 15000, // 15 000대
      iterations: 1, // 각 VU당 1회 요청
      maxDuration: "1m", // 1분 안에 모두 실행
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<500"], // 95th 응답시간 < 500ms
    http_req_failed: ["rate<0.01"], // 실패율 < 1%
  },
};
export default function () {
  sleep(Math.random() * 60);
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
