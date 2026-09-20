# -*- coding: utf-8 -*-
"""一次性探针：列 appliance 分区的 id 分布，看空号在哪、是否连续。"""
import os, re

HERE = os.path.dirname(os.path.abspath(__file__))
WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\WordBank.kt"
src = open(WB, encoding="utf-8").read()
m = re.search(r"private val APPLIANCE = listOf\((.*?)\n\)\n", src, re.S)
block = m.group(1)
ids = re.findall(r'term\(\s*"(appliance-\d+)"', block)
nums = sorted(int(i.split("-")[1]) for i in ids)
missing = [n for n in range(1, max(nums) + 1) if n not in set(nums)]
print("count=%d  min=%d  max=%d" % (len(ids), min(nums), max(nums)))
print("空号: %s" % (missing if missing else "无"))
print("顺序即文件顺序: %s" % (ids == ["appliance-%d" % n for n in nums]))
print("文件顺序前 5 / 后 5: %s ... %s" % (ids[:5], ids[-5:]))
