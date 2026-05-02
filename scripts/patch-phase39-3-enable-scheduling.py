#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "srmp-admin/src/main/java/com/smartroad/srmp/admin/SmartRoadApplication.java"
if not APP.exists():
    print("[FAIL] 文件不存在：" + str(APP)); sys.exit(1)
bak = APP.with_suffix(APP.suffix + ".phase39-3.bak")
if not bak.exists():
    bak.write_text(APP.read_text(encoding="utf-8"), encoding="utf-8")
s = APP.read_text(encoding="utf-8")
if "EnableScheduling" not in s:
    s = s.replace("import org.springframework.boot.autoconfigure.SpringBootApplication;\n",
                  "import org.springframework.boot.autoconfigure.SpringBootApplication;\nimport org.springframework.scheduling.annotation.EnableScheduling;\n")
    s = s.replace("@SpringBootApplication(scanBasePackages = \"com.smartroad.srmp\")",
                  "@EnableScheduling\n@SpringBootApplication(scanBasePackages = \"com.smartroad.srmp\")")
APP.write_text(s, encoding="utf-8")
print("[OK] enabled scheduling")
