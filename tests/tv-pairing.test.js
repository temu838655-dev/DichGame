const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const calls = [], statuses = [], subtitles = [], timers = [], store = {};
class Xhr {
  open(method, url){ this.url=url; }
  send(){ calls.push(this); }
  reply(status, data){ this.status=status; this.responseText=JSON.stringify(data); this.onload(); }
}
const sandbox={window:{},XMLHttpRequest:Xhr,setTimeout:fn=>timers.push(fn),localStorage:{getItem:k=>store[k]||null,setItem:(k,v)=>{store[k]=v},removeItem:k=>delete store[k]}};
vm.runInNewContext(fs.readFileSync('webos/pairing.js','utf8'),sandbox);
const connect=sandbox.window.DgReceiver.connect;
const stop=connect('192.168.1.10:8780',t=>subtitles.push(t),(msg,ready)=>statuses.push([msg,ready]));
assert.match(calls[0].url,/register$/);
calls[0].reply(200,{id:'request',code:'12345678'});
assert.match(statuses.at(-1)[0],/12345678/);
assert.match(calls[1].url,/wait\?id=request$/);
calls[1].reply(200,{token:'abc'});
assert.match(calls[2].url,/token=abc$/);
calls[2].reply(200,{seq:1,text:'Xin chào'});
assert.equal(subtitles.at(-1),'Xin chào');
assert.equal(statuses.at(-1)[1],true);
timers.shift()();
calls[3].reply(403,{});
assert.equal(subtitles.at(-1),'');
assert.match(calls[4].url,/register$/);
stop();
const count=calls.length;
calls[4].reply(200,{id:'ignore',code:'87654321'});
assert.equal(calls.length,count);
const node={style:{}},region={style:{}};
sandbox.window.DgReceiver.applyStyle(node,region,{style:{size:50,color:'#ffff00',background:60,top:true},region:'Menu'});
assert.equal(node.style.fontSize,'50px');
assert.equal(node.style.color,'#ffff00');
assert.equal(node.style.top,'7%');
assert.equal(region.style.bottom,'7%');
assert.equal(region.textContent,'Menu');
sandbox.window.DgReceiver.applyStyle(node,region,{region:''});
assert.equal(region.textContent,'');
assert.equal(node.style.top,'7%');
console.log('TV pairing and appearance transport passed');
