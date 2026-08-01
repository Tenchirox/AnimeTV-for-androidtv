/* Byse (gn1r5n.org) : localise le bouton "verifier humain" / gros bouton
   play central et envoie ses coordonnees a l'app (vrai geste Java).
   IMPORTANT : on s'arrete des que la video joue (video.paused==false) et
   on ignore la barre de controle (sinon on bascule play/pause en boucle). */
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

  /* La video joue-t-elle deja ? */
  function isPlaying(){
    try{
      var vids=document.querySelectorAll('video');
      for (var i=0;i<vids.length;i++){
        var v=vids[i];
        if (v && !v.paused && !v.ended && v.readyState>1 && v.currentTime>=0){
          return true;
        }
      }
    }catch(e){}
    return false;
  }

  var lastTapKey='', lastTapTime=0;
  function sendTap(el){
    try{
      var r=el.getBoundingClientRect();
      var x=Math.round(r.left + r.width/2);
      var y=Math.round(r.top + r.height/2);
      var key=x+','+y;
      var now=Date.now();
      /* ne re-tape pas le meme endroit pendant 3s (evite de marteler) */
      if (key===lastTapKey && (now-lastTapTime)<3000){ return false; }
      lastTapKey=key; lastTapTime=now;
      parent.postMessage(JSON.stringify({ type:'byse-tap', x:x, y:y }),'*');
      return true;
    }catch(e){ return false; }
  }

  function isBad(el){
    try{
      /* ignore tout ce qui est dans la barre de controle JW (play/pause,
         volume, fullscreen, next...) : ca bascule l'etat au lieu de jouer */
      if (el.closest && el.closest('.jw-controlbar, .jw-controls, .jw-dock, .jw-rightclick, [class*="controlbar"]')){
        return true;
      }
      var t=(el.textContent||'')+' '+(el.getAttribute('aria-label')||'');
      t=t.toLowerCase();
      return /(next|episode|ep\.|skip|setting|subtitle|caption|quality|volume|fullscreen|share|download|report|pause|rewind|forward|previous)/.test(t);
    }catch(e){ return false; }
  }

  function scan(){
    if (isPlaying()){ return 'playing'; }

    /* A) gros bouton play central de JW Player (display icon) */
    var central=[
      '.jw-display-icon-container','.jw-display','.jw-display-container',
      '.jw-icon-display','[class*="display-icon"]'
    ];
    var i, j, el;
    for (i=0;i<central.length;i++){
      var els=document.querySelectorAll(central[i]);
      for (j=0;j<els.length;j++){
        el=els[j];
        if (!vis(el) || isBad(el)) continue;
        if (sendTap(el)) return 'tapped';
      }
    }

    /* B) overlay de verification humain / demarrer */
    var all=document.querySelectorAll('button, [role="button"], a, div, span, input, h1, h2, h3, p');
    for (i=0;i<all.length;i++){
      el=all[i];
      if (!vis(el) || isBad(el)) continue;
      var t=((el.textContent||'')+(el.value||'')).toLowerCase();
      if (t.length>80) continue;
      if (/(human|verify|v[ée]rif|robot|captcha|continue|commencer|lancer|start watching|click to play|play now)/.test(t)){
        if (sendTap(el)) return 'tapped';
      }
    }

    /* C) gros element cliquable centre (bouton play generique) */
    var big=document.querySelectorAll('div, a, span, button');
    for (i=0;i<big.length;i++){
      el=big[i];
      if (!vis(el) || isBad(el)) continue;
      var r=el.getBoundingClientRect();
      var cx=r.left+r.width/2, cy=r.top+r.height/2;
      if (r.width>44 && r.width<360 && r.height>44 && r.height<360 &&
          Math.abs(cx-window.innerWidth/2)<window.innerWidth*0.2 &&
          Math.abs(cy-window.innerHeight/2)<window.innerHeight*0.2 &&
          el.children.length<=3){
        if (sendTap(el)) return 'tapped';
      }
    }
    return 'none';
  }

  var tries=0;
  var iv=setInterval(function(){
    tries++;
    var res='none';
    try{ res=scan(); }catch(e){}
    /* stop des que la video joue, ou apres 40 essais */
    if (res==='playing' || tries>40){
      clearInterval(iv);
    }
  }, 1200);
})();
