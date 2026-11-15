#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
联网激活服务（替代发卡）
- /api/register   设备登记（App 首次解锁时调用）
- /api/status     查询激活状态（App 轮询/启动时调用）
- /api/activate   标记已激活（管理端，需 token）
- /api/deactivate 取消激活（管理端，需 token）
- /admin          管理页（需 token）
"""
import os
import sqlite3
from datetime import datetime
from flask import Flask, request, jsonify, send_from_directory

app = Flask(__name__)

BASE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(BASE, "devices.db")
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "CHANGE_ME")

# ---- 恶搞段子库（防骚扰主题幽默段子，空表时自动导入） ----
JOKES_SEED = [
    ("诈骗电话", "骗子：您好，我是公安局的，您涉嫌洗钱。我：巧了，我还涉嫌没钱。"),
    ("诈骗电话", "骗子：您有一份快递涉嫌违禁品。我：是那个迟到了二十年的初恋吗？"),
    ("诈骗电话", "骗子：猜猜我是谁？我：是借了我五百块就失联的那位吗？先把钱还了。"),
    ("诈骗电话", "骗子：您的银行卡异常，请把资金转入安全账户。我：我账户一直是负数，麻烦你们先帮我充成正的。"),
    ("诈骗电话", "骗子：恭喜您中奖五百万！我：太好了，先帮我还完花呗，剩下的咱俩分。"),
    ("诈骗电话", "骗子：我们是法院的，您被起诉了。我：麻烦顺便帮我告一下那家骗我充值的健身房。"),
    ("诈骗电话", "骗子：先生，您儿子出车祸了需要手术费。我：我儿子是猫，它刚才还在拆我沙发。"),
    ("诈骗电话", "骗子：您在境外有一笔巨额消费。我：我在境外有一笔巨额幻想，谢谢你帮我确认了。"),
    ("诈骗电话", "骗子：我们是运营商的，您的手机即将停机。我：正好，帮我办个销号，谢谢。"),
    ("诈骗电话", "骗子：我是领导，明天来我办公室。我：领导，您先还我上次借的三百块路费。"),
    ("诈骗电话", "骗子：您的医保卡被盗刷了。我：太好了，刷走的都算我的，记得还我。"),
    ("诈骗电话", "骗子：您中了一台iPhone。我：能折现吗？骗子：可以。我：那先转我一百手续费，我转你五百万。"),
    ("诈骗电话", "骗子：我们是银监会的。我：巧了，我是中纪委的，你先报一下工号。"),
    ("诈骗电话", "骗子：您儿子被绑架了。我：帮我问问他，晚饭还回家吃吗？"),
    ("诈骗电话", "骗子：恭喜您获得一等奖。我：几等奖？骗子：一等奖！我：那二等奖是不是更厉害？"),
    ("推销骚扰", "推销：哥，买房吗？我：没钱。推销：那车呢？我：也没钱。推销：那来份保险？我：你见过连房车都买不起的人买得起保险吗？"),
    ("推销骚扰", "推销：我们这款保健品能治百病。我：能治穷病吗？"),
    ("推销骚扰", "推销：先生需要贷款吗？我：需要，贷一个亿，我要买下你们公司然后开除你。"),
    ("推销骚扰", "推销：您好，我们推出超值套餐。我：能换成超值退货套餐吗？"),
    ("推销骚扰", "推销：您对我们这个英语课程感兴趣吗？我：No。"),
    ("推销骚扰", "推销：您好，打扰一下，占用您一分钟。我：好，计时开始，你还有五十秒。"),
    ("推销骚扰", "推销：您好，办卡吗？我：办，给我办张「不要给我打电话」卡。"),
    ("推销骚扰", "推销：哥，健身了解一下。我：我现在每天起床已经是在健身了。"),
    ("推销骚扰", "推销：我们新楼盘首付只要十万。我：我连十万的「首」字都读不顺。"),
    ("推销骚扰", "推销：免费体验课了解一下。我：免费的话，把「体验」两个字也退了吧。"),
    ("推销骚扰", "推销：您最近有换车打算吗？我：有，我想把共享单车换成会员。"),
    ("推销骚扰", "推销：您好，是王总吗？我：王总在开会，我是王总的欠债，你找他什么事。"),
    ("推销骚扰", "推销：我们这款保险很便宜。我：便宜？那我买最便宜那一档，顺便再打个折。"),
    ("客服", "客服：您好，请问有什么可以帮您？我：帮我转人工。客服：我就是人工。我：那帮我转机器人，至少它不会气我。"),
    ("客服", "客服：请对我的服务打分，一到五分。我：零点五分，剩下是礼貌分。"),
    ("客服", "客服：亲，这边建议您重启一下试试。我：重启过了，现在连我都想重启了。"),
    ("客服", "客服：先生您别着急。我：我没着急，我手机已经替我着急得自动关机了。"),
    ("客服", "客服：亲，在吗？我：在，但我只负责「在」，不负责买。"),
    ("客服", "客服：给您造成困扰非常抱歉。我：没关系，我已经把困扰原路退回了。"),
    ("客服", "客服：您的问题已经记录。我：记在哪了？我看看能不能顺便把我也记录了。"),
    ("客服", "客服：先生，请您提供一下订单号。我：订单号没有，只有一串被拒接的骚扰电话号。"),
    ("客服", "客服：这边帮您转接专员。我：转了几次了，专员是不是都放假了？"),
]


def db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    return conn


def init():
    c = db()
    c.execute(
        """CREATE TABLE IF NOT EXISTS devices(
            machine TEXT PRIMARY KEY,
            activated INTEGER DEFAULT 0,
            created_at TEXT,
            activated_at TEXT)"""
    )
    c.execute(
        """CREATE TABLE IF NOT EXISTS downloads(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ip TEXT,
            ua TEXT,
            created_at TEXT)"""
    )
    c.execute(
        """CREATE TABLE IF NOT EXISTS events(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            machine TEXT,
            event TEXT,
            version TEXT,
            created_at TEXT)"""
    )
    c.execute(
        """CREATE TABLE IF NOT EXISTS jokes(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category TEXT,
            content TEXT,
            enabled INTEGER DEFAULT 1)"""
    )
    # 段子库为空时，导入内置段子
    if c.execute("SELECT COUNT(*) FROM jokes").fetchone()[0] == 0:
        c.executemany(
            "INSERT INTO jokes(category, content) VALUES(?,?)", JOKES_SEED
        )
    c.commit()
    c.close()


init()


def now():
    return datetime.now().isoformat(timespec="seconds")


@app.route("/api/register")
def register():
    m = (request.args.get("machine") or "").strip().upper()
    if not m or len(m) != 8:
        return jsonify({"ok": False, "error": "invalid machine"})
    c = db()
    c.execute(
        "INSERT OR IGNORE INTO devices(machine, activated, created_at) VALUES(?,0,?)",
        (m, now()),
    )
    c.commit()
    c.close()
    return jsonify({"ok": True})


@app.route("/api/status")
def status():
    m = (request.args.get("machine") or "").strip().upper()
    if not m:
        return jsonify({"activated": False})
    c = db()
    row = c.execute("SELECT activated FROM devices WHERE machine=?", (m,)).fetchone()
    c.close()
    return jsonify({"activated": bool(row and row["activated"])})


@app.route("/api/activate")
def activate():
    if request.args.get("token") != ADMIN_TOKEN:
        return jsonify({"ok": False, "error": "forbidden"}), 403
    m = (request.args.get("machine") or "").strip().upper()
    if not m:
        return jsonify({"ok": False, "error": "no machine"})
    c = db()
    c.execute(
        "UPDATE devices SET activated=1, activated_at=? WHERE machine=?",
        (now(), m),
    )
    c.commit()
    c.close()
    return jsonify({"ok": True})


@app.route("/api/deactivate")
def deactivate():
    if request.args.get("token") != ADMIN_TOKEN:
        return jsonify({"ok": False, "error": "forbidden"}), 403
    m = (request.args.get("machine") or "").strip().upper()
    c = db()
    c.execute("UPDATE devices SET activated=0 WHERE machine=?", (m,))
    c.commit()
    c.close()
    return jsonify({"ok": True})


ADMIN_HTML = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>激活管理</title>
<style>
body{font-family:-apple-system,sans-serif;margin:24px;background:#111;color:#eee}
table{border-collapse:collapse;width:100%;margin-top:16px}
th,td{border:1px solid #333;padding:8px 12px;text-align:left;font-size:14px}
th{background:#222}
.vip{color:#7ce8a0;font-weight:bold}.free{color:#aaa}
button{background:#4a6cf7;color:#fff;border:0;padding:6px 12px;border-radius:6px;cursor:pointer}
input{padding:6px 10px;border-radius:6px;border:1px solid #444;background:#222;color:#eee}
code{background:#222;padding:2px 6px;border-radius:4px}
</style></head><body>
<h2>🔑 激活管理</h2>
<p>📊 下载统计：{{stats}}</p>
<form action="/admin" method="get">
  token: <input type="text" name="token" value="{{token}}">
  <button type="submit">刷新</button>
</form>
<p>新设备激活：复制下方机器码，填进下面的输入框点「激活」。</p>
<form action="/api/activate" method="get">
  <input type="hidden" name="token" value="{{token}}">
  机器码 <input type="text" name="machine" placeholder="8位机器码" maxlength="8">
  <button type="submit">激活</button>
</form>
<table>
<tr><th>机器码</th><th>状态</th><th>登记时间</th><th>激活时间</th><th>操作</th></tr>
{{rows}}
</table>
</body></html>"""


@app.route("/admin")
def admin():
    if request.args.get("token") != ADMIN_TOKEN:
        return "forbidden", 403
    c = db()
    rows = c.execute("SELECT * FROM devices ORDER BY created_at DESC").fetchall()
    total = c.execute("SELECT COUNT(*) FROM downloads").fetchone()[0]
    today = c.execute(
        "SELECT COUNT(*) FROM downloads WHERE created_at >= date('now')"
    ).fetchone()[0]
    c.close()
    stats_html = f"总 <b>{total}</b> 次 · 今日 <b>{today}</b> 次"
    trs = []
    for r in rows:
        m = r["machine"]
        state = '<span class="vip">已激活</span>' if r["activated"] else '<span class="free">未激活</span>'
        op = (
            f'<a href="/api/deactivate?token={ADMIN_TOKEN}&machine={m}"><button>取消</button></a>'
            if r["activated"]
            else f'<a href="/api/activate?token={ADMIN_TOKEN}&machine={m}"><button>激活</button></a>'
        )
        trs.append(
            f"<tr><td><code>{m}</code></td><td>{state}</td>"
            f"<td>{r['created_at'] or ''}</td><td>{r['activated_at'] or ''}</td><td>{op}</td></tr>"
        )
    return ADMIN_HTML.replace("{{token}}", ADMIN_TOKEN).replace("{{rows}}", "".join(trs)).replace("{{stats}}", stats_html)


@app.route("/")
def index():
    c = db()
    total = c.execute("SELECT COUNT(*) FROM downloads").fetchone()[0]
    c.close()
    html = open(os.path.join(BASE, "index.html"), encoding="utf-8").read()
    return html.replace("__DOWNLOAD_COUNT__", str(total))


@app.route("/legalcall.apk")
def download_apk():
    ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "")[:64]
    ua = (request.headers.get("User-Agent") or "")[:200]
    c = db()
    c.execute(
        "INSERT INTO downloads(ip, ua, created_at) VALUES(?,?,?)",
        (ip, ua, now()),
    )
    c.commit()
    c.close()
    return send_from_directory(
        BASE, "legalcall.apk", as_attachment=True, download_name="legalcall-assistant.apk"
    )


@app.route("/api/stats")
def stats():
    c = db()
    total = c.execute("SELECT COUNT(*) FROM downloads").fetchone()[0]
    today = c.execute(
        "SELECT COUNT(*) FROM downloads WHERE created_at >= date('now')"
    ).fetchone()[0]
    c.close()
    return jsonify({"total": total, "today": today})


# ---- 恶搞段子 ----


@app.route("/api/jokes")
def jokes():
    c = db()
    rows = c.execute(
        "SELECT category, content FROM jokes WHERE enabled=1 ORDER BY id"
    ).fetchall()
    c.close()
    data = [{"category": r["category"], "content": r["content"]} for r in rows]
    return jsonify({"ok": True, "jokes": data})


# ---- 使用量统计（粗粒度、匿名、不碰隐私） ----

# 允许上报的事件白名单（防止乱塞脏数据）
ALLOWED_EVENTS = {
    "launch",        # 启动
    "试听话术",
    "立即脱身",
    "悬浮球脱身",
    "定时脱身",
    "打赏",
    "段子浏览",
    "段子随机",
    "段子朗读",
    "段子复制",
}


@app.route("/api/event")
def event():
    m = (request.args.get("machine") or "").strip().upper()[:16]
    ev = (request.args.get("event") or "").strip()
    ver = (request.args.get("version") or "").strip()[:16]
    if not m or ev not in ALLOWED_EVENTS:
        return jsonify({"ok": False, "error": "bad event"})
    c = db()
    c.execute(
        "INSERT INTO events(machine, event, version, created_at) VALUES(?,?,?,?)",
        (m, ev, ver, now()),
    )
    c.commit()
    c.close()
    return jsonify({"ok": True})


@app.route("/stats")
def stats_page():
    if request.args.get("token") != ADMIN_TOKEN:
        return "forbidden", 403
    c = db()
    total_dl = c.execute("SELECT COUNT(*) FROM downloads").fetchone()[0]
    today_dl = c.execute(
        "SELECT COUNT(*) FROM downloads WHERE created_at >= date('now')"
    ).fetchone()[0]
    active_devices = c.execute(
        "SELECT COUNT(DISTINCT machine) FROM events"
    ).fetchone()[0]
    today_active = c.execute(
        "SELECT COUNT(DISTINCT machine) FROM events WHERE created_at >= date('now')"
    ).fetchone()[0]
    # 各事件计数（按次数）
    ev_rows = c.execute(
        "SELECT event, COUNT(*) AS n FROM events GROUP BY event ORDER BY n DESC"
    ).fetchall()
    # 最近事件
    recent = c.execute(
        "SELECT machine, event, version, created_at FROM events ORDER BY id DESC LIMIT 20"
    ).fetchall()
    c.close()

    ev_html = "".join(
        f"<tr><td>{r['event']}</td><td>{r['n']}</td></tr>" for r in ev_rows
    ) or "<tr><td colspan=2>暂无数据</td></tr>"
    recent_html = "".join(
        f"<tr><td><code>{r['machine']}</code></td><td>{r['event']}</td>"
        f"<td>{r['version'] or ''}</td><td>{r['created_at']}</td></tr>"
        for r in recent
    ) or "<tr><td colspan=4>暂无</td></tr>"

    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>使用量统计</title>
<style>
body{{font-family:-apple-system,sans-serif;margin:24px;background:#111;color:#eee}}
.card{{background:#1c1c1c;border:1px solid #333;border-radius:12px;padding:16px;margin-bottom:16px}}
.grid{{display:flex;gap:12px;flex-wrap:wrap}}
.metric{{flex:1;min-width:120px;background:#222;border-radius:10px;padding:14px;text-align:center}}
.metric .v{{font-size:28px;font-weight:bold;color:#7ce8a0}}
.metric .k{{font-size:12px;color:#999;margin-top:4px}}
table{{border-collapse:collapse;width:100%;margin-top:10px}}
th,td{{border:1px solid #333;padding:6px 10px;text-align:left;font-size:13px}}
th{{background:#222}} code{{background:#222;padding:1px 5px;border-radius:4px}}
h2{{font-size:18px;margin:0 0 6px}}
</style></head><body>
<h2>📊 使用量统计</h2>
<div class="grid">
  <div class="metric"><div class="v">{total_dl}</div><div class="k">总下载</div></div>
  <div class="metric"><div class="v">{today_dl}</div><div class="k">今日下载</div></div>
  <div class="metric"><div class="v">{active_devices}</div><div class="k">活跃设备</div></div>
  <div class="metric"><div class="v">{today_active}</div><div class="k">今日活跃</div></div>
</div>
<div class="card"><h2>📈 功能使用次数</h2><table>
<tr><th>事件</th><th>次数</th></tr>{ev_html}</table></div>
<div class="card"><h2>🕒 最近上报</h2><table>
<tr><th>设备</th><th>事件</th><th>版本</th><th>时间</th></tr>{recent_html}</table></div>
</body></html>"""


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8981)
