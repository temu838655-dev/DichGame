/* ES5 LAN pairing transport for webOS and Tizen. */
(function(root){
function create(host, onText, onStatus, onFrame){
 var active=true, seq=-1, lastSpeech=null, current=null, token='', key='dg-receiver-'+host;
 try{token=localStorage.getItem(key)||'';}catch(e){}
 function later(fn,ms){if(active)setTimeout(function(){if(active)fn();},ms);}
 function req(path,cb){if(!active)return;var x=new XMLHttpRequest();current=x;x.open('GET','http://'+host+path,true);x.timeout=30000;x.onload=function(){if(!active)return;var data=null;try{data=JSON.parse(x.responseText);}catch(e){}cb(x.status,data);};x.onerror=x.ontimeout=function(){if(active)cb(0,null);};x.send();}
 function register(){onStatus('Đang xin mã ghép nối',false);req('/receiver/register',function(status,j){if(status!==200||!j||!j.id||!j.code){onStatus('Chưa lấy được mã, đang thử lại',false);later(register,5000);return;}onStatus('Mã ghép TV: '+j.code+' (nhập trên điện thoại)',false);wait(j.id);});}
 function wait(id){req('/receiver/wait?id='+encodeURIComponent(id),function(status,j){if(status===404){register();return;}if(status===200&&j&&j.token){token=j.token;try{localStorage.setItem(key,token);}catch(e){}poll();return;}later(function(){wait(id);},2000);});}
 function poll(){req('/receiver/poll?since='+seq+'&token='+encodeURIComponent(token),function(status,j){if(status===403){token='';seq=-1;try{localStorage.removeItem(key);}catch(e){}onText('');if(onFrame)onFrame({region:''});lastSpeech=null;register();return;}if(status===200&&j){seq=j.seq;onText(j.text||'',j.style);if(onFrame)onFrame(j);if(lastSpeech!==null&&j.speechSeq!==lastSpeech&&j.style&&j.style.speak&&j.speech){try{if(root.speechSynthesis&&root.SpeechSynthesisUtterance){var u=new root.SpeechSynthesisUtterance(j.speech);u.lang='vi-VN';root.speechSynthesis.cancel();root.speechSynthesis.speak(u);}}catch(e){}}lastSpeech=j.speechSeq;onStatus('Đã ghép TV · đang nhận phụ đề',true);later(poll,100);}else{seq=-1;lastSpeech=null;onText('');if(onFrame)onFrame({region:''});onStatus('Mất kết nối, đang thử lại',false);later(poll,1000);}});}
 if(token)poll();else register();
 return function(){active=false;if(current&&current.abort)current.abort();};
}
function applyStyle(node, region, frame){
 if(region)region.textContent=frame.region||'';
 if(!frame.style)return;
 var s=frame.style, size=Number(s.size);
 if(size>=24&&size<=80)node.style.fontSize=size+'px';
 if(/^#[0-9a-fA-F]{6}$/.test(s.color||''))node.style.color=s.color;
 var bg=Number(s.background);if(bg>=0&&bg<=100)node.style.backgroundColor='rgba(0,0,0,'+(bg/100)+')';
 node.style.top=s.top?'7%':'auto';node.style.bottom=s.top?'auto':'7%';
 if(region){region.textContent=frame.region||'';region.style.top=s.top?'auto':'7%';region.style.bottom=s.top?'7%':'auto';}
}
function captions(current, previous){
 var expiry=0, timer=null;
 function clearPrevious(){
  if(timer!==null){clearTimeout(timer);timer=null;}
  previous.textContent='';
  previous.style.animation='none';
 }
 return function(value,settings){
  var s=settings||{}, now=Date.now(), old=current.textContent, remaining=expiry-now;
  if(!value || s.keepPrevious===false)clearPrevious();
  if(old===value)return;
  clearPrevious();
  current.textContent=value;
  if(value && old && remaining>0 && s.keepPrevious!==false){
   previous.textContent=old;
   var fade=Math.min(350,remaining);
   void previous.offsetWidth;
   previous.style.animation='dgExit '+fade+'ms ease-in '+(remaining-fade)+'ms forwards';
   timer=setTimeout(clearPrevious,remaining);
  }
  var fixed=Number(s.lastSeconds);
  var hold=fixed>=2 && fixed<=8 ? fixed*1000 : Math.min(8000,Math.max(2000,600+value.replace(/\s/g,'').length*1000/17));
  expiry=value ? now+hold : 0;
 };
}
root.DgReceiver={connect:create,applyStyle:applyStyle,captions:captions};
}(window));
