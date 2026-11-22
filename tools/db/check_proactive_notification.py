#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""临时 QA：强制跑一次 ProactiveWorker，验证**主动消息通知**真的发出来了。

要验的是什么
------------
`dumpsys notification` 里能看到两类通知：
- `deep_focus_service`  —— 前台服务常驻通知（一直都在，跟主动 agent 无关）
- `deep_focus_proactive` —— 主动消息通知（**这才是要验的**）

只看「标题里有没有大肥鱼」会被前者误命中 —— 前台服务的标题就叫
「专注大肥鱼运行中」。所以必须按 **channel id** 过滤。

踩过的坑
--------
1. jobId 每跑一次就变（WorkManager 重新排期会换 id），
   必须「取 id → 立刻跑」，中间不能有 sleep。
2. `cmd jobscheduler run -f` 之后 JobScheduler 会打
   `Job didn't exist in JobStore` —— 那是 job 被消费掉的正常尾巴。
3. **WorkManager 的周期任务有「周期未到」闸门**：
   `last_enqueue_time` 是上一轮入队的时刻，周期没走完就直接跳过，
   `run_attempt_count` 一直是 0，worker 根本不执行。
   所以要先把 `last_enqueue_time` 拨回一个周期之前，
   模拟「这轮周期已经到了」。
4. logcat 必须**全程开着**抓 —— 事后再 `logcat -d` 常常已经滚掉了。

用法：
    python tools/db/check_proactive_notification.py
"""
import os
import re
import subprocess
import sys
import threading
import time

ADB = os.path.join(
    os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"
)
PKG = "com.deepfocus.app"
CHANNEL = "deep_focus_proactive"
WM_DB = "no_backup/androidx.work.workdb"

_lines = []


def adb(*args):
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout.decode("utf-8", errors="replace")


def start_logcat():
    def run():
        p = subprocess.Popen([ADB, "logcat", "-v", "time"],
                             stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        for raw in p.stdout:
            _lines.append(raw.decode("utf-8", "replace").rstrip())
    threading.Thread(target=run, daemon=True).start()


def find_wm_job_ids():
    """只取 WorkManager 的 job（SystemJobService）。

    App 有好几个 job：
    - `SystemJobService`           ← WorkManager 的（我们要的就是这个）
    - `.service.KeepAliveJobService` ← 前台服务保活，别碰
    """
    out = adb("shell", "dumpsys", "jobscheduler")
    ids = []
    for m in re.finditer(r"JOB #u0a\d+/(\d+):", out):
        chunk = out[m.start():m.start() + 600]
        if PKG in chunk and "SystemJobService" in chunk:
            ids.append(m.group(1))
    return ids


def proactive_notifications():
    """只返回走 proactive 渠道的通知（标题 + 正文）。"""
    notes = adb("shell", "dumpsys", "notification", "--noredact")
    hits = []
    for block in notes.split("NotificationRecord("):
        if f"channel={CHANNEL}" not in block:
            continue
        title = re.search(r"android\.title=String \(([^)]*)\)", block)
        text = re.search(r"android\.text=String \(([^)]*)\)", block)
        hits.append((title.group(1) if title else "", text.group(1) if text else ""))
    return hits


def workspec():
    return adb(
        "shell", f"run-as {PKG} sqlite3 {WM_DB} "
        "'SELECT state, run_attempt_count, last_enqueue_time FROM workspec;'"
    ).strip()


def backdate_period():
    """把周期拨回一个周期之前 —— 让 WorkManager 认为「这轮该跑了」。

    周期 30 分钟，拨回 40 分钟。
    """
    now_ms = int(time.time() * 1000)
    back = now_ms - 40 * 60 * 1000
    return adb(
        "shell", f"run-as {PKG} sqlite3 {WM_DB} "
        f"'UPDATE workspec SET last_enqueue_time = {back};'"
    ).strip()


def interesting_logs():
    keep = []
    for line in _lines:
        if ("ProactiveWorker" in line or "FishNotifier" in line
                or "主动唤醒" in line or "已发通知" in line
                or "WM-" in line or "WorkManager" in line):
            keep.append(line)
    return keep


def main():
    print("强制前 workspec:", workspec())
    adb("logcat", "-c")
    start_logcat()
    time.sleep(2)

    deadline = time.time() + 300
    attempts = 0
    while time.time() < deadline and attempts < 8:
        attempts += 1
        # 重启 App：`ProactiveEngine` 的「最小检查间隔」是进程内变量
        # （lastCheckAt，3 分钟），不重启的话第二次强制 worker 一定被
        # 「距上次检查仅 N 秒」挡掉，掷骰根本没机会发生。
        adb("shell", "am", "force-stop", PKG)
        time.sleep(1)
        adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
        time.sleep(4)

        ids = find_wm_job_ids()
        if not ids:
            time.sleep(2)
            continue
        jid = ids[0]
        print(f"-- 第 {attempts} 轮，周期拨回：{backdate_period() or 'ok'}")
        res = adb("shell", "cmd", "jobscheduler", "run", "-f", PKG, jid).strip()
        print(f"   jobId {jid} -> {res}")
        for _ in range(6):
            time.sleep(3)
            hits = proactive_notifications()
            if hits:
                print(">>> proactive 渠道通知:")
                for t, x in hits:
                    print(f"    标题={t!r}  正文={x!r}")
                print("   workspec:", workspec())
                return 0
    print("没等到 proactive 渠道通知")
    print("--- 相关日志 ---")
    for line in interesting_logs()[-40:]:
        print("  ", line)
    print("--- workspec ---")
    print("  ", workspec())
    print("--- 当前全部通知 ---")
    notes = adb("shell", "dumpsys", "notification", "--noredact")
    for m in re.finditer(r"channel=(\S+)", notes):
        print("  channel:", m.group(1))
    for m in re.finditer(r"android\.(?:title|text)=String \(([^)]*)\)", notes):
        print("  ", m.group(1))
    return 1


if __name__ == "__main__":
    sys.exit(main())
