import paramiko, os

local = "/home/mint/.openclaw/workspace/gongka-guardian/app/build/outputs/apk/debug/app-debug.apk"
remote = "/home/ubuntu/legalcall-server/gongka.apk"

pw = os.environ.get("SRV_PW", "")
if not pw:
    with open(os.path.expanduser("~/.srv_pw"), "r") as f:
        pw = f.read().strip()

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("49.233.207.110", username="ubuntu", password=pw, timeout=20)
sftp = c.open_sftp()
sftp.put(local, remote)
sftp.close()
stdin, stdout, stderr = c.exec_command("ls -la ~/legalcall-server/gongka.apk")
print(stdout.read().decode())
c.close()
print("done")
