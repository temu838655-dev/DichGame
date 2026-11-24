"""Create/reuse only the named D1 database and bind its UUID in the checkout."""
import json
import os
import re
import sys
import urllib.request
import urllib.parse
import urllib.error
from pathlib import Path

NAME = "dichgame-community"
account = os.getenv("CLOUDFLARE_ACCOUNT_ID", "")
token = os.getenv("CLOUDFLARE_API_TOKEN", "")
admin = os.getenv("ADMIN_TOKEN", "")
client = os.getenv("CLIENT_TOKEN", "")
if not re.fullmatch(r"[a-fA-F0-9]{32}", account) or not token:
    sys.exit("Missing or invalid CLOUDFLARE_ACCOUNT_ID / CLOUDFLARE_API_TOKEN.")
if not 24 <= len(admin) <= 500 or not 24 <= len(client) <= 500 or admin == client:
    sys.exit("Set distinct DICHGAME_ADMIN_TOKEN and DICHGAME_CLIENT_TOKEN (24–500 characters).")

base = f"https://api.cloudflare.com/client/v4/accounts/{account}/d1/database"
def api(method, url, data=None):
    req = urllib.request.Request(url, method=method,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
        data=json.dumps(data).encode() if data is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.load(response)
    except urllib.error.HTTPError as e:
        sys.exit(f"Cloudflare D1 API returned HTTP {e.code}; check token permissions.")
    if not result.get("success"):
        sys.exit("Cloudflare D1 API rejected the request; check account and D1 permissions.")
    return result["result"]

rows = api("GET", base + "?" + urllib.parse.urlencode({"name": NAME, "per_page": 100}))
matches = [row for row in rows if row.get("name") == NAME]
if len(matches) > 1:
    sys.exit("Multiple databases matched; refusing ambiguous deployment.")
db = matches[0] if matches else api("POST", base, {"name": NAME})
db_id = db.get("uuid", "")
if not re.fullmatch(r"[0-9a-fA-F-]{36}", db_id):
    sys.exit("Cloudflare did not return a valid D1 UUID.")
config = Path(__file__).with_name("wrangler.toml")
src = config.read_text()
src, count = re.subn(r'^database_id\s*=\s*"[^"]+"\s*$', f'database_id = "{db_id}"', src, flags=re.M)
if count != 1:
    sys.exit("Expected exactly one D1 binding in wrangler.toml.")
config.write_text(src)
print("D1 database bound successfully (created or reused).")
