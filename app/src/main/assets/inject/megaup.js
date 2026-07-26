
console.log = null;
console.warn = null;
console.error = null;
var _trueWindow=window;
_trueWindow.real_atob=_trueWindow.atob;
_trueWindow.real_btoa=_trueWindow.btoa;
_trueWindow.encodeURIComponentTmp=_trueWindow.encodeURIComponent;
_trueWindow.decodeURIComponentTmp=_trueWindow.decodeURIComponent;
_trueWindow.JSON.parseOri=_trueWindow.JSON.parse;
_trueWindow.JSON.parseCurrent=_trueWindow.JSON.parseOri;
_trueWindow.JSON.parse=function(a){
    return _trueWindow.JSON.parseCurrent(a);
};
_trueWindow.decryptMega=null;
_trueWindow.encryptKai=null;
_trueWindow.decryptKai=null;

(function req(){
    _trueWindow.console.log = null;
    _trueWindow.console.warn = null;
    _trueWindow.console.error = null;
    /*
    var lPath=location.pathname+"";
    if (lPath.startsWith('/watch')){
        _trueWindow.addEventListener("beforeunload", function (e) {
            var confirmationMessage = "\o/";
            (e || _trueWindow.event).returnValue = confirmationMessage;
            return confirmationMessage;
        });
    }
    */
})();
(function(jqw){
    function megaDecrypt(id, data){
        if (_trueWindow.decryptMega){
            _trueWindow.decryptMega(data, function(res){
                parent.postMessage(JSON.stringify({
                        megaupcmd:"decryptresult",
                        megaupid:id,
                        data:res
                }),'*');
            });
        }
    }
    function kaiEncrypt(id, data){
        if (_trueWindow.encryptKai){
            _trueWindow.encryptKai(data, function(res){
                parent.postMessage(JSON.stringify({
                        kaicmd:"codexresult",
                        kaiid:id,
                        data:res
                }),'*');
            });
        }
    }
    function kaiDecrypt(id, data){
        if (_trueWindow.decryptKai){
            _trueWindow.decryptKai(data, function(res){
                parent.postMessage(JSON.stringify({
                        kaicmd:"codexresult",
                        kaiid:id,
                        data:res
                }),'*');
            });
        }
    }

    /* message handler */
    window.addEventListener('message',function(e) {
        try{
            var j=JSON.parseOri(e.data);
            if ('megaupcmd' in j){
                if (j.megaupcmd=="decrypt"){
                    megaDecrypt(j.megaupid, j.data);
                }
            }
            else if ('kaicmd' in j){
                if (j.kaicmd=="encrypt"){
                    kaiEncrypt(j.kaiid, j.data);
                }
                else if (j.kaicmd=="decrypt"){
                    kaiDecrypt(j.kaiid, j.data);
                }
            }
        }catch(e){}
    });

    function rollbackFn(){
        _trueWindow.atob=_trueWindow.real_atob;
        _trueWindow.btoa=_trueWindow.real_btoa;
        _trueWindow.decodeURIComponent=_trueWindow.decodeURIComponentTmp;
        _trueWindow.encodeURIComponent=_trueWindow.encodeURIComponentTmp;
        _trueWindow.JSON.parseCurrent=_trueWindow.JSON.parseOri;
    }
    
    /********** MEGA ************/
    var cbFn=null;
    function hackMega(cb){
        cbFn = cb;
        _trueWindow.decryptMega=function(txt, resCb){
            var beforeJson=true;
            let resVal={
                status:200,
                result: txt
            };
            _trueWindow.JSON.parseCurrent=function(u){
                beforeJson=false;
                resCb(u);
                return null;
            };
            try{
                cb(resVal);
            }catch(ee){}
            if (beforeJson){
                resCb(null);
            }
            rollbackFn();
        };
    }

    /********** HOME ************/
    var homeEncFn=null;
    var homeDecFn=null;
    function hackKaihome(cb){
        _trueWindow.homeDecFnGlobal=homeDecFn=cb;
        _trueWindow.decryptKai=function(txt, resCb){
            var beforeJson=true;
            var eRes = {
                "status":200,
                "result":txt
            };
            _trueWindow.JSON.parseCurrent=function(u){
                beforeJson=false;
                resCb(u);
                return null;
            };
            try{
                homeDecFn(eRes);
            }catch(ee){}
            if (beforeJson){
                resCb(null);
            }
            rollbackFn();
        };
        document.body.innerHTML='KAICODEX HOME HOOKED';
        parent.postMessage(JSON.stringify({
            kaicmd:"initial"
        }),'*');
    }
    function hackHome(t,i){
        if (t && ('beforeSend' in t) && (typeof t.beforeSend == 'function')){
            if (!_trueWindow.encryptKai){
                homeEncFn=t.beforeSend;
                _trueWindow.encryptKai=function(txt, resCb){
                    var udata = {
                        "url":"/?_=strict"+_trueWindow.encodeURIComponentTmp(txt)
                    };
                    try{
                        homeEncFn(udata,udata);
                    }catch(ee){}
                    var rdata = udata.url.split("=");
                    resCb(rdata[rdata.length-1]);
                };
            }
        }
        return jqw.ajaxSetupOri(t,i);
    }


    /* CHECK CURRENT URL */
    var hookDone=null;
    var hookFn=null;
    if (location.host=="megaup.live"){
        hookDone=function(u,o){
            return ((typeof u === 'string') && (u.startsWith("/media")));
        };
        hookFn=hackMega;
        parent.postMessage(JSON.stringify({
            megaupcmd:"initial"
        }),'*');
        document.body.innerHTML='';
    }
    else if (location.host=="animekai.to"){
        var lPath=location.pathname+"";
        if (lPath.startsWith('/home')){
            _trueWindow.addEventListener('load', function(){
                location=document.querySelector("a.poster").href;
            });
        }
        else if (lPath.startsWith('/watch')){
            function getPlayer(){
                let player=document.querySelector("#player button");
                if (player){
                    try{
                        player.click();
                    }catch(e){}
                    setTimeout(getPlayer, 5);
                }
            }
            _trueWindow.addEventListener('load', function(){
                getPlayer();
            });
            jqw.ajaxSetupOri=jqw.ajaxSetup;
            jqw.ajaxSetup=hackHome;
            hookDone=function(u,o){
                if (typeof u === 'string'){
                    return false;
                }
                if ('url' in u){
                    console.info(["INFO hookDone", u.url]);
                    return (u.url.indexOf("/ajax/links/view")>-1);
                }
                return false;
            };
            hookFn=hackKaihome;
        }
    }

    /* Hook Ajax */
    if (hookDone && hookFn){
        jqw.ajaxOri=jqw.ajax;
        jqw.ajax=function(u,o){
            if (hookDone(u,o)) {
                let x = jqw.ajaxOri(u,o);
                x.realDone=x.done;
                x.done=function(res){
                    _trueWindow.__done=res;
                    hookFn(res);
                    /*return x.realDone(res);*/
                };
                return x;
            }
            return jqw.ajaxOri(u,o);
        };
    }
})(_trueWindow.$);
