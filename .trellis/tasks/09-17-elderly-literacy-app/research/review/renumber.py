# -*- coding: utf-8 -*-
"""把 design.md 的 v3 节编号统一为 §8（连续编号：7 → 8 → 9），
并同步代码注释里的 §8.x 引用。改动前逐条断言出现次数，防误替换。"""
import io, os

TASK = r"D:\QYJ\MyProject\Mando\.trellis\tasks\09-17-elderly-literacy-app"
FILES = {
    os.path.join(TASK, "design.md"): [
        ("## 9. v3：每日队列调度与滑动即播", "## 8. v3：每日队列调度与滑动即播", 1),
        ("### 9.1 数据模型变更", "### 8.1 数据模型变更", 1),
        ("### 9.2 队列生成（调度器，在 StudyRepository）", "### 8.2 队列生成（调度器，在 StudyRepository）", 1),
        ("### 9.3 朗读契约（AppRoot）", "### 8.3 朗读契约（AppRoot）", 1),
        ("### 9.4 持久化时机", "### 8.4 持久化时机", 1),
        ("### 9.5 涉及文件", "### 8.5 涉及文件", 1),
        ("### 9.6 分区毕业（2026-09-17 决策）", "### 8.6 分区毕业（2026-09-17 决策）", 1),
        ("## 10. 取舍与风险", "## 9. 取舍与风险", 1),
        ("（§9.6）", "（§8.6）", 1),
        ("朗读契约 §9.3", "朗读契约 §8.3", 1),
        ("毕业（§9.6）", "毕业（§8.6）", 0),   # 允许 0 次
    ],
    r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\qyj\shibang\ui\AppRoot.kt": [
        ("design.md §9）", "design.md §8）", 1),
        ("design.md §9.3", "design.md §8.3", 2),
        ("§9.6）：该区每个词", "§8.6）：该区每个词", 1),
    ],
}

for path, reps in FILES.items():
    with io.open(path, encoding="utf-8-sig") as f:
        s = f.read()
    for old, new, expect in reps:
        n = s.count(old)
        if n != expect:
            print("SKIP (count=%d expect=%d): %s" % (n, expect, old))
            if n == 0:
                continue
        s = s.replace(old, new)
        print("OK  x%d  %s  ->  %s" % (n, old[:40], new[:40]))
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)
    print("written:", os.path.basename(path), len(s), "chars")
