/* Byse (gn1r5n.org) : auto-clic sur le bouton "verifier que vous etes
   humain" / lecture. Le clic est fait programmatiquement (le CSS de l'app
   met pointer-events:none sur l'iframe, mais ca n'affecte pas le JS).
   On relaie aussi la progression via postMessage vers l'app. */
(function(){
  if (window.__byseHook){ return; }
  window.__byseHook=true;

  function vis(el){
    try{
      if (!el) return false;
      var r=el.getBoundingClientRect();
      return r.width>4 && r.height>4 && el.offsetParent!==null;
    }catch(e){ return false; }
  }

  function clickCandidate(el){
    try{
      var evs=['pointerdown','mousedown','pointerup','mouseup','click'];
      for (var i=0;i<evs.length;i++){
        try{
          el.dispatchEvent(new MouseEvent(evs[i],{bubbles:true,cancelable:true,view:window}));
        }catch(e){}
      }
      el.click();
      return true;
    }catch(e){ return false; }
  }

  function scan(){
    /* 1) selecteurs de boutons/overlays de lecture/verification */
    var sels=[
      'button','[role="button"]','.vjs-big-play-button','.play-button',
      '.play','#play','[class*="big-play"]','[class*="play-btn"]',
      '[class*="verify"]','[class*="captcha"]','[class*="human"]',
      '[class*="continue"]','[class*="start"]'
    ];
    var i, j, el;
    for (i=0;i<sels.length;i++){
      var els=document.querySelectorAll(sels[i]);
      for (j=0;j<els.length;j++){
        el=els[j];
        if (!vis(el)) continue;
        var t=(el.textContent||'').toLowerCase();
        /* evite les boutons "suivant"/"episode" etc */
        if (/(next|episode|ep\.|skip|setting|subtitle|caption)/.test(t)) continue;
        if (clickCandidate(el)) return true;
      }
    }
    /* 2) scan textuel generique (verification humain / lancer la lecture) */
    var all=document.querySelectorAll('button, [role="button"], a, div, span, input, h1, h2, h3, p');
    for (i=0;i<all.length;i++){
      el=all[i];
      if (!vis(el)) continue;
      var t=((el.textContent||'')+(el.value||'')).toLowerCase();
      if (t.length>80) continue;
      if (/(human|verify|v[ée]rif|robot|captcha|continue|lecture|play|start|watch|commencer|regarder|lancer)/.test(t)){
        if (clickCandidate(el)) return true;
      }
    }
    /* 3) gros overlay central (souvent le bouton play) */
    var big=document.querySelectorAll('div, a, span');
    for (i=0;i<big.length;i++){
      el=big[i];
      if (!vis(el)) continue;
      var r=el.getBoundingClientRect();
      var cx=r.left+r.width/2, cy=r.top+r.height/2;
      /* centre de l'ecran, taille raisonnable d'un bouton play */
      if (r.width>40 && r.width<320 && r.height>40 && r.height<320 &&
          Math.abs(cx-window.innerWidth/2)<window.innerWidth*0.2 &&
          Math.abs(cy-window.innerHeight/2)<window.innerHeight*0.2 &&
          el.children.length<=3){
        if (clickCandidate(el)) return true;
      }
    }
    return false;
  }

  var tries=0;
  var iv=setInterval(function(){
    tries++;
    try{ scan(); }catch(e){}
    if (tries>60){ clearInterval(iv); }
  }, 900);

  /* Relai de la progression Byse -> app (au cas ou le pont parent ne capte pas) */
  try{
    var origPM=window.postMessage;
    /* on laisse le comportement natif : l'app ecoute deja les messages */
  }catch(e){}
})();
