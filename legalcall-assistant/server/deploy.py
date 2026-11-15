#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SSH 到服务器执行命令（paramiko）。"""
import os
import sys
import paramiko

HOST = "49.233.207.110"
USER = "ubuntu"


def _load_password():
    """密码从环境变量 SRV_PW 或 ~/.srv_pw 读取，不硬编码。"""
    pw = os.environ.get("SRV_PW", "")
    if pw:
        return pw
    with open(os.path.expanduser("~/.srv_pw")) as f:
        return f.read().strip()


PASSWORDS = [_load_password()]


def connect():
    for pw in PASSWORDS:
        try:
            c = paramiko.SSHClient()
            c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            c.connect(HOST, username=USER, password=pw, timeout=15)
            return c, pw
        except Exception as e:
            last = e
    raise RuntimeError(f"连接失败: {last}")


def run(cmd):
    c, _ = connect()
    stdin, stdout, stderr = c.exec_command(cmd, timeout=60)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    c.close()
    return code, out, err


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: deploy.py <命令>")
        sys.exit(1)
    cmd = sys.argv[1]
    code, out, err = run(cmd)
    print(f"--- exit={code} ---")
    if out:
        print(out)
    if err:
        print("STDERR:", err)
