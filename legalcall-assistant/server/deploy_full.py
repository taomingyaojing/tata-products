#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""完整部署：上传代码 -> venv 装依赖 -> systemd 服务 -> 自检。"""
import os
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

SERVICE = """[Unit]
Description=LegalCall Activation Server
After=network.target

[Service]
User=ubuntu
WorkingDirectory={dir}
ExecStart={dir}/venv/bin/python {dir}/app.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
"""


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


def run(c, cmd):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=180)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def main():
    c, pw = connect()
    print(f"[1] 已连接 (密码: {'***'})")

    # 建目录 + 上传
    run(c, f"mkdir -p {REMOTE_DIR}")
    sftp = c.open_sftp()
    for f in ["app.py", "requirements.txt"]:
        sftp.put(os.path.join(LOCAL_DIR, f), f"{REMOTE_DIR}/{f}")
        print(f"[2] 已上传 {f}")
    sftp.close()

    # venv + 装依赖
    print("[3] 创建 venv 并安装 Flask ...")
    code, out, err = run(
        c,
        f"cd {REMOTE_DIR} && python3 -m venv venv && "
        f"./venv/bin/pip install -q -r requirements.txt",
    )
    if code != 0:
        print("!! venv/pip 失败:\n", err)
    else:
        print("[3] Flask 安装完成")

    # 写 systemd 服务
    svc = SERVICE.format(dir=REMOTE_DIR)
    sftp = c.open_sftp()
    with sftp.open("/tmp/legalcall.service", "w") as f:
        f.write(svc)
    sftp.close()
    run(c, "sudo cp /tmp/legalcall.service /etc/systemd/system/legalcall.service")
    run(c, "sudo systemctl daemon-reload")
    run(c, "sudo systemctl enable legalcall")
    run(c, "sudo systemctl restart legalcall")
    print("[4] systemd 服务已启动")

    # 自检
    import time
    time.sleep(2)
    code, out, err = run(c, "curl -s http://127.0.0.1:8981/api/status?machine=TEST1234")
    print(f"[5] 自检 /api/status -> {out.strip()}")
    code, out, err = run(c, "systemctl is-active legalcall")
    print(f"[6] 服务状态: {out.strip()}")

    c.close()
    print("完成 ✅")


if __name__ == "__main__":
    main()
