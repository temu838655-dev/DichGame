import {adminHtml} from "./admin.mjs";
// Self-hosted private beta. Only administrator-approved rows can be returned to apps.
export const normalize = value => value.trim().replace(/\s+/gu, " ").toLowerCase();
const bounded = (value, max, min = 1) => typeof value === "string" && value.trim().length >= min &&
  value.length <= max && !/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/u.test(value);
export function parseLine(body) {
  if (!body || !bounded(body.game, 180) || !["en","zh"].includes(body.lang) ||
      !["dialogue","menu"].includes(body.kind) || !bounded(body.speaker ?? "", 120, 0) ||
      !bounded(body.original, 500, 12)) throw new Error("invalid_line");
  return [normalize(body.game), body.lang, normalize(body.speaker ?? ""), body.kind, normalize(body.original)];
}
const reply = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: {"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}
});
async function jsonBody(request) {
  const reader = request.body?.getReader();
  if (!reader) throw new Error("invalid_body");
  const parts = []; let size = 0;
  try {
    for (;;) {
      const {done,value} = await reader.read(); if (done) break;
      size += value.length; if (size > 16384) throw new Error("body_too_large");
      parts.push(value);
    }
  } finally { await reader.cancel().catch(()=>{}); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const p of parts) {bytes.set(p,offset); offset += p.length;}
  return JSON.parse(new TextDecoder().decode(bytes));
}
async function authorized(request, secret) {
  if (typeof secret !== "string" || secret.length < 24) return false;
  const provided = request.headers.get("Authorization") ?? "";
  if (provided.length > 512) return false;
  const enc = new TextEncoder();
  const [a,b] = await Promise.all([crypto.subtle.digest("SHA-256",enc.encode(provided)),crypto.subtle.digest("SHA-256",enc.encode("Bearer "+secret))]);
  const aa = new Uint8Array(a), bb = new Uint8Array(b); let diff = 0;
  for (let i=0;i<aa.length;i++) diff |= aa[i]^bb[i];
  return diff === 0;
}
async function budget(env, operation, limit) {
  const day = new Date().toISOString().slice(0,10);
  const row = await env.DB.prepare("INSERT INTO daily_usage(day,operation,count) VALUES(?,?,1) ON CONFLICT(day,operation) DO UPDATE SET count=count+1 WHERE count<? RETURNING count")
    .bind(day,operation,limit).first();
  return row !== null;
}
const lookupSQL = "SELECT vi FROM translations WHERE game_key=? AND lang=? AND speaker_key=? AND kind=? AND source_key=?";
export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname, method = request.method;
    if (path === "/v1/health" && method === "GET") return reply({ok:true,version:1});
    if (path === "/admin" && method === "GET") {
      const nonce=Array.from(crypto.getRandomValues(new Uint8Array(16)),x=>x.toString(16).padStart(2,"0")).join("");
      return new Response(adminHtml.replaceAll("__NONCE__",nonce),{headers:{"Content-Type":"text/html; charset=utf-8","Cache-Control":"no-store","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer","Content-Security-Policy":"default-src 'none'; script-src 'nonce-"+nonce+"'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"}});
    }
    const admin = path.startsWith("/v1/admin/");
    if (!await authorized(request,admin?env.ADMIN_TOKEN:env.CLIENT_TOKEN)) return reply({error:"unauthorized"},401);
    try {
      if (!admin && !await budget(env,"read",20000)) return reply({error:"daily_limit"},429);
      if (path === "/v1/lookup" && method === "POST") {
        const body=await jsonBody(request), key=parseLine(body);
        const row=await env.DB.prepare(lookupSQL).bind(...key).first();
        return reply({vi:row?.vi ?? null});
      }
      if (path === "/v1/contribute" && method === "POST") {
        const body=await jsonBody(request), key=parseLine(body);
        if (!bounded(body.vi,1400,3) || normalize(body.vi)===key[4]) return reply({error:"invalid_translation"},400);
        if (!await budget(env,"contribute",1000)) return reply({error:"daily_limit"},429);
        if (await env.DB.prepare(lookupSQL).bind(...key).first()) return reply({status:"already_approved"});
        await env.DB.prepare("INSERT OR IGNORE INTO submissions(game_key,lang,speaker_key,kind,source_key,original,vi) VALUES(?,?,?,?,?,?,?)")
          .bind(...key,body.original.trim(),body.vi.trim()).run();
        return reply({status:"pending_review"},202);
      }
      if (path === "/v1/status" && method === "GET") {
        const row=await env.DB.prepare("SELECT COUNT(*) AS count FROM catalog").first();
        return reply({ok:true,version:2,games:row?.count??0});
      }
      if (path === "/v1/catalog" && method === "GET") {
        const {results}=await env.DB.prepare("SELECT title,genre FROM catalog ORDER BY title LIMIT 1000").all();
        return reply({games:results});
      }
      if (path === "/v1/pack" && method === "POST") {
        const body=await jsonBody(request);
        if (!bounded(body.game,180) || !["en","zh"].includes(body.lang)) return reply({error:"invalid_game"},400);
        const after=typeof body.after==="string"?body.after:"";
        if (!bounded(after,500,0)) return reply({error:"invalid_cursor"},400);
        const {results}=await env.DB.prepare("SELECT source_key,original,vi FROM packs WHERE game_key=? AND lang=? AND source_key>? ORDER BY source_key LIMIT 101")
          .bind(normalize(body.game),body.lang,after).all();
        const page=results.slice(0,100);
        return reply({game:normalize(body.game),lang:body.lang,lines:page.map(({source_key,original,vi})=>({source_key,original,vi})),next:results.length>100?page.at(-1).source_key:null});
      }
      if (path === "/v1/admin/stats" && method === "GET") {
        const row=await env.DB.prepare("SELECT (SELECT COUNT(*) FROM catalog) AS catalog,(SELECT COUNT(*) FROM translations) AS translations,(SELECT COUNT(*) FROM packs) AS packs,(SELECT COUNT(*) FROM submissions) AS submissions").first();
        return reply(row);
      }
      if (path === "/v1/admin/pending" && method === "GET") {
        const {results}=await env.DB.prepare("SELECT * FROM submissions ORDER BY id LIMIT 100").all();
        return reply({submissions:results});
      }
      if (path === "/v1/admin/review" && method === "POST") {
        const body=await jsonBody(request);
        if (!Number.isSafeInteger(body.id)||body.id<1||!["approve","reject"].includes(body.action)) return reply({error:"invalid_review"},400);
        if(body.action==="approve") {
          // First approved result wins; a later candidate cannot overwrite it.
          await env.DB.batch([
            env.DB.prepare("INSERT OR IGNORE INTO translations(game_key,lang,speaker_key,kind,source_key,original,vi) SELECT game_key,lang,speaker_key,kind,source_key,original,vi FROM submissions WHERE id=?").bind(body.id),
            env.DB.prepare("DELETE FROM submissions WHERE id=?").bind(body.id)
          ]);
        } else await env.DB.prepare("DELETE FROM submissions WHERE id=?").bind(body.id).run();
        return reply({ok:true});
      }
      if(path==="/v1/admin/catalog" && method==="POST") {
        const body=await jsonBody(request);
        if(!bounded(body.game,180)||!bounded(body.genre??"",180,0)) return reply({error:"invalid_game"},400);
        await env.DB.prepare("INSERT INTO catalog(game_key,title,genre) VALUES(?,?,?) ON CONFLICT(game_key) DO UPDATE SET title=excluded.title,genre=excluded.genre,updated_at=unixepoch()")
          .bind(normalize(body.game),body.game.trim(),(body.genre??"").trim()).run();
        return reply({ok:true});
      }
      if(path==="/v1/admin/pack-batch" && method==="POST") {
        const body=await jsonBody(request);
        if(!bounded(body.game,180)||!["en","zh"].includes(body.lang)||!Array.isArray(body.lines)||body.lines.length<1||body.lines.length>30||body.lines.some(x=>!x||!bounded(x.original,500)||!bounded(x.vi,1400))) return reply({error:"invalid_pack"},400);
        const sql="INSERT INTO packs(game_key,lang,source_key,original,vi) VALUES(?,?,?,?,?) ON CONFLICT(game_key,lang,source_key) DO UPDATE SET original=excluded.original,vi=excluded.vi,updated_at=unixepoch()";
        await env.DB.batch(body.lines.map(x=>env.DB.prepare(sql).bind(normalize(body.game),body.lang,normalize(x.original),x.original.trim(),x.vi.trim())));
        return reply({ok:true,count:body.lines.length});
      }
      if(path==="/v1/admin/pack" && method==="POST") {
        const body=await jsonBody(request);
        if(!bounded(body.game,180)||!["en","zh"].includes(body.lang)||!bounded(body.original,500)||!bounded(body.vi,1400)) return reply({error:"invalid_pack"},400);
        await env.DB.prepare("INSERT INTO packs(game_key,lang,source_key,original,vi) VALUES(?,?,?,?,?) ON CONFLICT(game_key,lang,source_key) DO UPDATE SET original=excluded.original,vi=excluded.vi,updated_at=unixepoch()")
          .bind(normalize(body.game),body.lang,normalize(body.original),body.original.trim(),body.vi.trim()).run();
        return reply({ok:true});
      }
      if(path==="/v1/admin/delete" && method==="POST") {
        const body=await jsonBody(request);
        if(!bounded(body.game,180)) return reply({error:"invalid_game"},400);
        const key=normalize(body.game);
        await env.DB.batch(["translations","submissions","packs","catalog"].map(table=>env.DB.prepare("DELETE FROM "+table+" WHERE game_key=?").bind(key)));
        return reply({ok:true});
      }
      if (path === "/v1/admin/gta6/check" && method === "POST") {
        const body = await jsonBody(request);
        if (!Array.isArray(body.ids) || body.ids.length < 1 || body.ids.length > 100 || body.ids.some(id => !bounded(id,160))) return reply({error:"invalid_gta6_ids"},400);
        const rows = [];
        for (const id of [...new Set(body.ids.map(id => id.trim()))]) {
          const row = await env.DB.prepare("SELECT external_id,status,translated_text FROM gta6_localization WHERE game_key=? AND external_id=?").bind("grand theft auto vi",id).first();
          if (row) rows.push(row);
        }
        return reply({lines:rows});
      }
      if (path === "/v1/admin/gta6/pending" && method === "GET") {
        const {results} = await env.DB.prepare("SELECT external_id,source_text,translated_text,group_name,sheet_name,row_number,status FROM gta6_localization WHERE game_key=? AND status IN ('PENDING','AI_UNVERIFIED') ORDER BY id LIMIT 100").bind("grand theft auto vi").all();
        return reply({lines:results});
      }
      if (path === "/v1/admin/gta6/batch" && method === "POST") {
        const body = await jsonBody(request);
        if (normalize(body.game ?? "") !== "grand theft auto vi" || body.rightsConfirmed !== true || !Array.isArray(body.lines) || body.lines.length < 1 || body.lines.length > 30) return reply({error:"invalid_gta6_batch"},400);
        const statements = [];
        for (const line of body.lines) {
          if (!line || !bounded(line.external_id,160) || !bounded(line.original,700) || !bounded(line.group_name ?? "",180,0) || !bounded(line.sheet_name ?? "",80,0) || !Number.isSafeInteger(line.row_number) || line.row_number < 1 || !bounded(line.vi ?? "",1400,0)) return reply({error:"invalid_gta6_line"},400);
          const old = await env.DB.prepare("SELECT source_text,status FROM gta6_localization WHERE game_key=? AND external_id=?").bind("grand theft auto vi",line.external_id.trim()).first();
          if (old && old.source_text !== line.original.trim()) return reply({error:"external_id_conflict",external_id:line.external_id},409);
          if (old?.status === "VERIFIED") continue;
          const vi = (line.vi ?? "").trim();
          const status = vi ? "AI_UNVERIFIED" : "PENDING";
          const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(line.original.trim()))),b=>b.toString(16).padStart(2,"0")).join("");
          statements.push(env.DB.prepare("INSERT INTO gta6_localization(game_key,external_id,sheet_name,row_number,group_name,source_text,normalized_text,translated_text,status,source_sha256,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,unixepoch()) ON CONFLICT(game_key,external_id) DO UPDATE SET sheet_name=excluded.sheet_name,row_number=excluded.row_number,group_name=excluded.group_name,translated_text=CASE WHEN gta6_localization.status='VERIFIED' THEN gta6_localization.translated_text ELSE excluded.translated_text END,status=CASE WHEN gta6_localization.status='VERIFIED' THEN 'VERIFIED' ELSE excluded.status END,source_sha256=excluded.source_sha256,updated_at=unixepoch()").bind("grand theft auto vi",line.external_id.trim(),(line.sheet_name ?? "").trim(),line.row_number,(line.group_name ?? "").trim(),line.original.trim(),normalize(line.original),vi,status,hash));
        }
        if (statements.length) await env.DB.batch(statements);
        return reply({ok:true,received:body.lines.length,upserted:statements.length,verified_preserved:body.lines.length-statements.length});
      }
      if (path === "/v1/admin/gta6/review" && method === "POST") {
        const body = await jsonBody(request);
        if (!bounded(body.external_id,160) || !["approve","reject"].includes(body.action)) return reply({error:"invalid_gta6_review"},400);
        const status = body.action === "approve" ? "VERIFIED" : "REJECTED";
        const result = await env.DB.prepare("UPDATE gta6_localization SET status=?,updated_at=unixepoch() WHERE game_key=? AND external_id=? AND (?='REJECTED' OR length(trim(translated_text))>0)").bind(status,"grand theft auto vi",body.external_id.trim(),status).run();
        if (!result.meta?.changes) return reply({error:"line_not_found_or_empty"},404);
        return reply({ok:true,status});
      }
      
      return reply({error:"not_found"},404);
    } catch(error) {
      if(error instanceof SyntaxError || ["invalid_line","invalid_body","body_too_large"].includes(error?.message)) return reply({error:"invalid_request"},400);
      return reply({error:"service_unavailable"},503);
    }
  }
};
