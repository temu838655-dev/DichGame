/* ES5 LAN pairing transport for webOS and Tizen. */
(function(root){
function create(host, onText, onStatus){
 var active=true, seq=-1, token='', key='dg-receiver-'+host;
 try{token=localStorage.getItem(key)||'';}catch(e){}
 function later(fn,ms){if(active)setTimeout(function(){if(active)fn();},ms);}
 function req(path,cb){if(!active)return;var x=new XMLHttpRequest();x.open('GET','http://'+host+path,true);x.timeout=30000;x.onload=function(){if(!active)return;var data=null;try{data=JSON.parse(x.responseText);}catch(e){}cb(x.status,data);};x.onerror=x.ontimeout=function(){if(active)cb(0,null);};x.send();}
 function register(){onStatus('Đang xin mã ghép nối',false);req('/receiver/register',function(status,j){if(status!==200||!j||!j.id||!j.code){onStatus('Chưa lấy được mã, đang thử lại',false);later(register,5000);return;}onStatus('Mã ghép TV: '+j.code+' (nhập trên điện thoại)',false);wait(j.id);});}
 function wait(id){req('/receiver/wait?id='+encodeURIComponent(id),function(status,j){if(status===404){register();return;}if(status===200&&j&&j.token){token=j.token;try{localStorage.setItem(key,token);}catch(e){}poll();return;}later(function(){wait(id);},2000);});}
 function poll(){req('/receiver/poll?since='+seq+'&token='+encodeURIComponent(token),function(status,j){if(status===403){token='';seq=-1;try{localStorage.removeItem(key);}catch(e){}onText('');register();return;}if(status===200&&j){seq=j.seq;onText(j.text||'');onStatus('Đã ghép TV · đang nhận phụ đề',true);later(poll,100);}else{seq=-1;onText('');onStatus('Mất kết nối, đang thử lại',false);later(poll,1000);}});}
 if(token)poll();else register();
 return function(){active=false;};
}
root.DgReceiver={connect:create};
}(window));
