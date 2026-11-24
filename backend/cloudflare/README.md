# DichGame shared translation database (private beta)

Independent backend; no affiliation with any other translator. D1 starts empty.
Only publish game data and translations you have permission to distribute.
A client token is suitable for a small trusted group, not a public app-wide secret.
Do not put either token in GitHub, APK source, or a public release. For public rollout,
replace shared client credentials with individual accounts/tokens and per-user rate limits.
No account or payment system is required for this beta.

## Deploy on your own Cloudflare account
Requires Node.js and an authenticated Cloudflare account. Use the current Wrangler 4 CLI.
Run these from this directory:
```sh
npx wrangler@4 login
npx wrangler@4 d1 create dichgame-community
# Copy returned database_id into wrangler.toml.
npx wrangler@4 d1 migrations apply dichgame-community --remote
npx wrangler@4 secret put ADMIN_TOKEN
npx wrangler@4 secret put CLIENT_TOKEN
npx wrangler@4 deploy
```
Use distinct random tokens of at least 24 characters (32+ recommended).
Keep ADMIN_TOKEN solely with the owner. Enter the deployed HTTPS origin and
CLIENT_TOKEN in Android > Cách dịch > Kho dữ liệu trực tuyến.
Turn on lookup and test connection. Contributions require separate consent.
Do not give anyone your Cloudflare login, AI keys or admin token.
Workers/D1 free tier has limits; monitor requests and storage in Cloudflare.
Set a spending policy before switching to a paid plan.

## API
All routes except GET /v1/health require Authorization: Bearer TOKEN.
Use client token for lookup, catalog, pack and contribution. Use admin token
for /v1/admin/* only. TLS required by app. No raw API keys for AI, screenshots,
audio, profile names or PSN data are uploaded. Source line, speaker, game,
language and optional AI translation are sent; network service may see IP.
Daily shared beta limits: 20,000 client requests and 1,000 contributions.
These are guardrails, not a guarantee against abuse or provider quotas.

- POST /v1/lookup {game,lang,speaker,kind,original}: only approved exact match.
- POST /v1/contribute same fields plus vi: pending review; cannot overwrite approved rows.
- GET /v1/catalog: approved game names and genres.
- POST /v1/pack {game,lang,after}: paged reviewed lines.
- GET /v1/admin/pending: pending rows (up to 100).
- POST /v1/admin/review {id,action:"approve"|"reject"}: approve or discard a candidate.
- POST /v1/admin/catalog {game,genre}: add/update public catalog.
- POST /v1/admin/pack {game,lang,original,vi}: publish reviewed line.
- POST /v1/admin/delete {game}: remove all server data for the specified game.

For example, with BASE and ADMIN_TOKEN held in your terminal environment:
```sh
curl -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/v1/admin/pending"
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":1,"action":"approve"}' "$BASE/v1/admin/review"
```
Admin review is intentionally manual so a bad/poisoned translation cannot immediately
spread to users. Inspect pending data before approval. Never distribute other projects'
private data. Users can disable lookup/contribution; ask the operator to remove
previously sent data. Local SQLite remains independent and usable offline.
