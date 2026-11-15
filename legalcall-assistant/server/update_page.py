#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""更新下载页 + APK + app.py 到服务器并重启。"""
import os
import time
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
REMOTE_DIR = "/home/ubuntu/legalcall-server"
LOCAL_DIR = os.path.dirname(os.path.abspath(__file__))
APK_SRC = os.path.join(LOCAL_DIR, "..", "app", "build", "outputs", "apk", "debug", "app-debug.apk")


def connect():
    for pw in PASSWORDS:
        try:
            c = paramiko.SSHClient()
            c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            c.connect(HOST, username=USER, password=pw, timeout=15)
            return c
        except Exception as e:
            last = e
    raise RuntimeError(f"连接失败: {last}")


def run(c, cmd):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=180)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def main():
    c = connect()
    sftp = c.open_sftp()
    sftp.put(os.path.join(LOCAL_DIR, "index.html"), f"{REMOTE_DIR}/index.html")
    print("[1] index.html 上传完成")
    if os.path.exists(APK_SRC):
        sftp.put(APK_SRC, f"{REMOTE_DIR}/legalcall.apk")
        print(f"[2] legalcall.apk 上传完成 ({os.path.getsize(APK_SRC)//1024} KB)")
    else:
        print("!! 找不到 APK:", APK_SRC)
    sftp.put(os.path.join(LOCAL_DIR, "app.py"), f"{REMOTE_DIR}/app.py")
    print("[3] app.py 上传完成")
    sftp.close()

    run(c, "sudo systemctl restart legalcall")
    time.sleep(2)
    code, out, err = run(c, "systemctl is-active legalcall")
    print(f"[4] 服务状态: {out.strip()}")

    code, out, err = run(c, "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8981/")
    print(f"[5] 首页 HTTP: {out}")
    code, out, err = run(c, "curl -s -o /dev/null -w '%{http_code} %{size_download}' http://127.0.0.1:8981/legalcall.apk")
    print(f"[6] APK 下载 HTTP+大小: {out}")
    c.close()
    print("完成 ✅")


if __name__ == "__main__":
    main()
