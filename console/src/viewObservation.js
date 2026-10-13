// 一次实际激活观看的状态；预加载不创建观察，暂停和循环不结束观察。
export function createViewObservation(identity, report, now = () => performance.now()) {
  // 捕获本次视频与请求标识，退出时的异步发送不会跟随页面当前条目变化。
  const context = Object.freeze({ ...identity });
  let startedAt = null;
  let watchMs = 0;
  let hasPlayed = false;
  // 媒体播放状态与计时状态分开：后台隐藏时媒体可能继续播放，但有效观看时长停止累计。
  let playing = false;
  let visible = true;
  let seeking = false;
  let cycleInvalid = false;
  // terminal 表示已上报本次观看结果，disposed 表示观察已退出；完播后循环仍可展示但不再上报。
  let terminal = false;
  let disposed = false;

  // 只累计 playing 且可见、未跳转的片段，暂停/缓冲后恢复不会把间隔算入 watch_ms。
  function stopClock() {
    if (startedAt !== null) watchMs += Math.max(0, now() - startedAt);
    startedAt = null;
  }
  function startClock() {
    if (!terminal && playing && visible && !seeking && startedAt === null) startedAt = now();
  }
  function emit(type, completed, keepalive = false) {
    // 服务端 watch_ms 是非负 INT，使用单调时钟累计后取整并限制到该范围。
    const duration = Math.min(2147483647, Math.max(0, Math.round(watchMs)));
    // 不重试非幂等流水；网络失败不传播到播放控制。
    try {
      Promise.resolve(report({ ...context, event_type: type, watch_ms: duration, completed }, keepalive)).catch(() => {});
    } catch { /* 同步发送失败同样不阻塞观看 */ }
  }
  emit("exposed", false);
  return {
    signal(kind) {
      if (disposed || terminal) return;
      if (kind === "playing") {
        playing = true;
        hasPlayed = true;
        startClock();
      } else if (["pause", "waiting", "stalled"].includes(kind)) {
        stopClock();
        playing = false;
      } else if (kind === "seeking") {
        stopClock();
        seeking = true;
        // 用户跳到末尾不能算一次完整观看，本轮 ended 后下一轮可重新判定完播。
        cycleInvalid = true;
      } else if (kind === "seeked") {
        seeking = false;
        startClock();
      } else if (kind === "ended") {
        stopClock();
        playing = false;
        if (!cycleInvalid && hasPlayed) {
          // 完播只汇总一次有效时长，后续重播和退出都不再生成 play/complete/skip。
          terminal = true;
          emit("play", true);
          emit("complete", true);
        }
        cycleInvalid = false;
      }
    },
    setVisible(value) {
      if (disposed) return;
      stopClock();
      visible = value;
      startClock();
    },
    finish() {
      if (disposed) return;
      disposed = true;
      stopClock();
      if (terminal) return;
      terminal = true;
      // 未开始播放就离开只记录 skip；play 在退出时汇总，keepalive 尝试覆盖导航离开。
      if (hasPlayed) emit("play", false, true);
      emit("skip", false, true);
    }
  };
}
