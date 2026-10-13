import test from "node:test";
import assert from "node:assert/strict";
import { createViewObservation } from "./viewObservation.js";

function fixture(report) {
  let ms = 0;
  const events = [];
  const observation = createViewObservation({ video_id: 3, scene: "recommend", request_id: "req-1" },
    report || ((body, keepalive) => events.push({ ...body, keepalive })), () => ms);
  return { observation, events, advance: (delta) => { ms += delta; } };
}

test("退出前尚未播放，只记录曝光和跳过；重复清理不重复上报", () => {
  const { observation, events } = fixture();
  observation.finish(); observation.finish();
  assert.deepEqual(events.map(e => e.event_type), ["exposed", "skip"]);
  assert.equal(events[1].watch_ms, 0);
  assert.equal(events[1].keepalive, true);
});
test("有效时长排除暂停、缓冲、后台隐藏和跳转", () => {
  const { observation: o, events, advance } = fixture();
  o.signal("playing"); advance(1000);
  o.signal("pause"); advance(3000);
  o.signal("playing"); advance(1000);
  o.signal("waiting"); advance(2000);
  o.signal("playing"); advance(500);
  o.setVisible(false); advance(4000); o.setVisible(true); advance(500);
  o.signal("seeking"); advance(1000); o.signal("seeked"); advance(1000);
  o.finish();
  assert.deepEqual(events.map(e => e.event_type), ["exposed", "play", "skip"]);
  assert.equal(events[1].watch_ms, 4000);
  assert.equal(events[2].completed, false);
});
test("首轮完播只上报一次汇总播放和完播，后续循环与离开不重复", () => {
  const { observation: o, events, advance } = fixture();
  o.signal("playing"); advance(2000); o.signal("ended");
  o.signal("seeking"); o.signal("seeked"); o.signal("playing"); advance(2000); o.signal("ended"); o.finish();
  assert.deepEqual(events.map(e => e.event_type), ["exposed", "play", "complete"]);
  assert.equal(events[1].watch_ms, 2000);
  assert.equal(events[1].completed, true);
  assert.equal(events[2].completed, true);
});
test("跳到末尾不计完整播放，下一完整循环仍可完成", () => {
  const { observation: o, events, advance } = fixture();
  o.signal("playing"); advance(500); o.signal("seeking"); o.signal("seeked"); o.signal("ended");
  assert.equal(events.length, 1);
  o.signal("playing"); advance(2000); o.signal("ended");
  assert.equal(events[2].event_type, "complete");
});
test("观察捕获视频和请求标识，离开与重新进入有独立信号", () => {
  const a = fixture(); a.observation.finish();
  const b = fixture(); b.observation.finish();
  assert.equal(a.events.length, 2); assert.equal(b.events.length, 2);
  for (const event of a.events) {
    assert.equal(event.video_id, 3); assert.equal(event.scene, "recommend"); assert.equal(event.request_id, "req-1");
  }
});
test("发送失败不阻塞状态，且没有自动重试", async () => {
  let calls = 0;
  const { observation } = fixture(() => { calls++; return Promise.reject(new Error("offline")); });
  observation.signal("playing"); observation.finish();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls, 3);
});
