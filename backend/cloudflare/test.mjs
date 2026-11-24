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
