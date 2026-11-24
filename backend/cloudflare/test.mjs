import {test} from "node:test";
import assert from "node:assert/strict";
import worker,{parseLine,normalize} from "./src/index.mjs";
const token="client-secret-for-testing-only-123456";
const admin="admin-secret-for-testing-only-123456";
const env={CLIENT_TOKEN:token,ADMIN_TOKEN:admin};
test("exact scoped keys, normalization and length",()=>{
  assert.deepEqual(parseLine({game:" Game ",lang:"zh",speaker:" A ",kind:"dialogue",original:"我們現在要去哪個地方呢？"}),["game","zh","a","dialogue","我們現在要去哪個地方呢？"]);
  assert.throws(()=>parseLine({game:"G",lang:"ja",kind:"dialogue",original:"A long enough sentence"}));
  assert.equal(normalize(" A  B "),"a b");
});
test("anonymous and wrong-role calls rejected before D1",async()=>{
  for(const [path,auth] of [["/v1/catalog",""],["/v1/admin/pending",token],["/v1/catalog",admin]]) {
    const r=await worker.fetch(new Request("https://test.invalid"+path,{headers:{Authorization:"Bearer "+auth}}),env);
    assert.equal(r.status,401);
  }
});
test("health endpoint",async()=>assert.equal((await worker.fetch(new Request("https://test.invalid/v1/health"),env)).status,200));

test("admin page does not expose credentials and has restrictive CSP",async()=>{const r=await worker.fetch(new Request("https://test.invalid/admin"),env);assert.equal(r.status,200);assert.match(r.headers.get("content-security-policy"),/frame-ancestors 'none'/);const body=await r.text();assert.ok(!body.includes(admin)&&!body.includes(token));assert.match(body,/Bản dịch chờ duyệt/);});
test("client cannot access bulk publish or admin stats",async()=>{for(const [path,method] of [["stats","GET"],["pack-batch","POST"]]){const r=await worker.fetch(new Request("https://test.invalid/v1/admin/"+path,{method,headers:{Authorization:"Bearer "+token}}),env);assert.equal(r.status,401)}});
test("malformed batch is rejected without database writes",async()=>{const r=await worker.fetch(new Request("https://test.invalid/v1/admin/pack-batch",{method:"POST",headers:{Authorization:"Bearer "+admin},body:JSON.stringify({game:"Test",lang:"en",lines:[{original:"x",vi:""}]})}),env);assert.equal(r.status,400)});
