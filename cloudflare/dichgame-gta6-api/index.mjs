const GAME = 'grand theft auto vi';
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
  'Access-Control-Max-Age': '86400',
};
const json = (body, status = 200, cache = 'public, max-age=60') => new Response(JSON.stringify(body), {
  status, headers: { ...cors, 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': cache, 'X-Content-Type-Options': 'nosniff' },
});
const normalize = value => String(value ?? '').normalize('NFKC').trim().replace(/\s+/gu, ' ').toLocaleLowerCase('en-US');
const bad = (message, status = 400) => json({ error: message }, status, 'no-store');

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
    const url = new URL(request.url);
    if (request.method !== 'GET') return bad('method_not_allowed', 405);
    try {
      if (url.pathname === '/v1/gta6/health') return json({ ok: true, service: 'dichgame-gta6', version: 1 }, 200, 'no-store');
      if (url.pathname === '/v1/gta6/manifest') {
        const row = await env.DB.prepare(
          "SELECT COUNT(*) AS total, SUM(CASE WHEN status='VERIFIED' AND provenance_status='VERIFIED' THEN 1 ELSE 0 END) AS verified, MAX(updated_at) AS updated_at FROM gta6_localization WHERE game_key=?"
        ).bind(GAME).first();
        return json({ game: 'Grand Theft Auto VI', version: 1, total: row?.total ?? 0, verified: row?.verified ?? 0, updated_at: row?.updated_at ?? null, id_lookup: true, text_lookup: true });
      }
      if (url.pathname === '/v1/gta6/lookup') {
        const id = url.searchParams.get('id')?.trim() ?? '';
        const text = url.searchParams.get('text') ?? '';
        if ((!id && !text) || id.length > 160 || text.length > 700) return bad('invalid_lookup');
        let row = null;
        if (id) {
          row = await env.DB.prepare(
            "SELECT external_id, source_text, translated_text, group_name FROM gta6_localization WHERE game_key=? AND external_id=? AND status='VERIFIED' AND provenance_status='VERIFIED' LIMIT 1"
          ).bind(GAME, id).first();
        }
        const context = url.searchParams.get('context')?.trim() ?? '';
        let ambiguous = false;
        if (!row && text) {
          if (context) {
            row = await env.DB.prepare(
              "SELECT external_id, source_text, translated_text, group_name FROM gta6_localization WHERE game_key=? AND normalized_text=? AND group_name=? AND status='VERIFIED' AND provenance_status='VERIFIED' ORDER BY id LIMIT 1"
            ).bind(GAME, normalize(text), context).first();
          } else {
            const matches = await env.DB.prepare(
              "SELECT MIN(id) AS id, external_id, source_text, translated_text, group_name FROM gta6_localization WHERE game_key=? AND normalized_text=? AND status='VERIFIED' AND provenance_status='VERIFIED' GROUP BY translated_text LIMIT 2"
            ).bind(GAME, normalize(text)).all();
            if (matches.results.length === 1) row = matches.results[0];
            else if (matches.results.length > 1) ambiguous = true;
          }
        }
        return json({ hit: !!row, ambiguous, match: row ? (id && row.external_id === id ? 'id' : 'exact_text') : null, translation: row?.translated_text ?? null, external_id: row?.external_id ?? null, context: row?.group_name ?? null });
      }
      if (url.pathname === '/v1/gta6/lines') {
        const cursorRaw = url.searchParams.get('after') ?? '0';
        const limitRaw = Number(url.searchParams.get('limit') ?? '200');
        const cursor = Number(cursorRaw), limit = Number.isInteger(limitRaw) ? Math.max(1, Math.min(500, limitRaw)) : 200;
        if (!Number.isSafeInteger(cursor) || cursor < 0) return bad('invalid_cursor');
        const { results } = await env.DB.prepare(
          "SELECT id, external_id, source_text, translated_text, group_name FROM gta6_localization WHERE game_key=? AND status='VERIFIED' AND provenance_status='VERIFIED' AND id>? ORDER BY id LIMIT ?"
        ).bind(GAME, cursor, limit + 1).all();
        const page = results.slice(0, limit);
        return json({ game: 'Grand Theft Auto VI', lines: page, next: results.length > limit ? page[page.length - 1].id : null });
      }
      return bad('not_found', 404);
    } catch (_) {
      return bad('service_unavailable', 503);
    }
  },
};
