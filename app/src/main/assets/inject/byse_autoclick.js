/* Byse (gn1r5n.org) : localise le bouton "verifier humain" / play et
   envoie ses coordonnees a l'app. L'app dispatch ensuite un VRAI
   MotionEvent (geste utilisateur reel) a cet endroit via Java, car un
   el.click() JS ne compte pas pour la politique autoplay. */
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

  /* Envoie les coordonnees (centre de l'element) au parent -> Java tap */
  function sendTap(el){
    try{
      var r=el.getBoundingClientRect();
      var x=r.left + r.width/2;
      var y=r.top + r.height/2;
      window.__byseLastTap=(window.__byseLastTap||0);
      var now=Date.now();
      if (now-window.__byseLastTap<1500) return false; /* anti-spam */
      window.__byseLastTap=now;
      parent.postMessage(JSON.stringify({
        type:'byse-tap', x:x, y:y
      }),'*');
      return true;
    }catch(e){ return false; }
  }

  function isBad(el){
    var t=(el.textContent||'').toLowerCase();
    return /(next|episode|ep\.|skip|setting|subtitle|caption|quality|volume|fullscreen|share|download|report)/.test(t);
  }

  function scan(){
    /* 1) bouton play JW Player + verification */
    var sels=[
      '.jw-icon-playback','.jw-display-icon-container','[aria-label*="Play"]',
      '[aria-label*="play"]','.jwplayer [role="button"]',
      '.vjs-big-play-button','.play-button','#play',
      '[class*="big-play"]','[class*="play-btn"]',
      '[class*="verify"]','[class*="captcha"]','[class*="human"]',
      '[class*="continue"]','[class*="start"]'
    ];
    var i, j, el;
    for (i=0;i<sels.length;i++){
      var els=document.querySelectorAll(sels[i]);
      for (j=0;j<els.length;j++){
        el=els[j];
        if (!vis(el) || isBad(el)) continue;
        if (sendTap(el)) return true;
      }
    }
    /* 2) scan textuel generique (verification humain) */
    var all=document.querySelectorAll('button, [role="button"], a, div, span, input, h1, h2, h3, p');
    for (i=0;i<all.length;i++){
      el=all[i];
      if (!vis(el) || isBad(el)) continue;
      var t=((el.textContent||'')+(el.value||'')).toLowerCase();
      if (t.length>80) continue;
      if (/(human|verify|v[ée]rif|robot|captcha|continue|commencer|lancer|start watching|click to play)/.test(t)){
        if (sendTap(el)) return true;
      }
    }
    /* 3) gros overlay central (bouton play centre) */
    var big=document.querySelectorAll('div, a, span, button');
    for (i=0;i<big.length;i++){
      el=big[i];
      if (!vis(el) || isBad(el)) continue;
      var r=el.getBoundingClientRect();
      var cx=r.left+r.width/2, cy=r.top+r.height/2;
      if (r.width>40 && r.width<360 && r.height>40 && r.height<360 &&
          Math.abs(cx-window.innerWidth/2)<window.innerWidth*0.22 &&
          Math.abs(cy-window.innerHeight/2)<window.innerHeight*0.22 &&
          el.children.length<=3){
        if (sendTap(el)) return true;
      }
    }
    return false;
  }

  var tries=0;
  var iv=setInterval(function(){
    tries++;
    try{ scan(); }catch(e){}
    if (tries>80){ clearInterval(iv); }
  }, 1000);
})();
