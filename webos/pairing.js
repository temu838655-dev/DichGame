/* ES5 LAN pairing transport for webOS and Tizen. */
(function(root){
function create(host, onText, onStatus, onFrame){
 var active=true, seq=-1, lastSpeech=null, token='', key='dg-receiver-'+host;
 try{token=localStorage.getItem(key)||'';}catch(e){}
 function later(fn,ms){if(active)setTimeout(function(){if(active)fn();},ms);}
 function req(path,cb){if(!active)return;var x=new XMLHttpRequest();x.open('GET','http://'+host+path,true);x.timeout=30000;x.onload=function(){if(!active)return;var data=null;try{data=JSON.parse(x.responseText);}catch(e){}cb(x.status,data);};x.onerror=x.ontimeout=function(){if(active)cb(0,null);};x.send();}
 function register(){onStatus('Đang xin mã ghép nối',false);req('/receiver/register',function(status,j){if(status!==200||!j||!j.id||!j.code){onStatus('Chưa lấy được mã, đang thử lại',false);later(register,5000);return;}onStatus('Mã ghép TV: '+j.code+' (nhập trên điện thoại)',false);wait(j.id);});}
 function wait(id){req('/receiver/wait?id='+encodeURIComponent(id),function(status,j){if(status===404){register();return;}if(status===200&&j&&j.token){token=j.token;try{localStorage.setItem(key,token);}catch(e){}poll();return;}later(function(){wait(id);},2000);});}
 function poll(){req('/receiver/poll?since='+seq+'&token='+encodeURIComponent(token),function(status,j){if(status===403){token='';seq=-1;try{localStorage.removeItem(key);}catch(e){}onText('');register();return;}if(status===200&&j){seq=j.seq;onText(j.text||'');if(onFrame)onFrame(j);if(lastSpeech!==null&&j.speechSeq!==lastSpeech&&j.style&&j.style.speak&&j.speech){try{if(root.speechSynthesis&&root.SpeechSynthesisUtterance){var u=new root.SpeechSynthesisUtterance(j.speech);u.lang='vi-VN';root.speechSynthesis.cancel();root.speechSynthesis.speak(u);}}catch(e){}}lastSpeech=j.speechSeq;onStatus('Đã ghép TV · đang nhận phụ đề',true);later(poll,100);}else{seq=-1;onText('');onStatus('Mất kết nối, đang thử lại',false);later(poll,1000);}});}
 if(token)poll();else register();
 return function(){active=false;};
}
function applyStyle(node, region, frame){
 var s=frame.style||{}, size=Number(s.size);
 if(size>=24&&size<=80)node.style.fontSize=size+'px';
 if(/^#[0-9a-fA-F]{6}$/.test(s.color||''))node.style.color=s.color;
 var bg=Number(s.background);if(bg>=0&&bg<=100)node.style.backgroundColor='rgba(0,0,0,'+(bg/100)+')';
 node.style.top=s.top?'7%':'auto';node.style.bottom=s.top?'auto':'7%';
 if(region){region.textContent=frame.region||'';region.style.top=s.top?'auto':'7%';region.style.bottom=s.top?'7%':'auto';}
}
root.DgReceiver={connect:create,applyStyle:applyStyle};
}(window));
