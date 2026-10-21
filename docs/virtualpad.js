/* 虚拟按键桥：DOM 按键/游戏区点击/物理键盘 -> window.__pad -> TeaVM(Java HostInput) */
(function () {
  'use strict';

  var pending = window.__padJava; // TeaVM 启动后注入
  function pad(code, down) {
    if (window.__padJava) window.__padJava(code, down);
  }
  window.__pad = pad; // 供 autotap 等外部驱动使用

  // ---------- 素材捕获模式（?dump=1，构建期高清重制用） ----------
  if (location.search.indexOf('dump=1') >= 0) {
    window.__hdDump = {};
    setInterval(function () {
      try {
        if (Object.keys(window.__hdDump).length) {
          fetch('/dump', { method: 'POST', body: JSON.stringify(window.__hdDump) });
        }
      } catch (e) {}
    }, 4000);
  }

  // ---------- 画布自适应：双画质模式 ----------
  // 锐利=整数倍像素完美（每逻辑像素恰 k×k 设备像素，零重采样，画面略小有边框）
  // 铺满=ceil 整数倍超采样渲染 + 平滑微缩（画面最大，约 5% 均匀柔和）
  var LOGICAL_W = 240, LOGICAL_H = 320;
  var displayMode = 'sharp';
  try {
    displayMode = localStorage.getItem('hsqst_display') === 'fill' ? 'fill' : 'sharp';
  } catch (e) {}
  var mq = location.search.match(/quality=(fill|sharp)/);
  if (mq) {
    displayMode = mq[1];
    try { localStorage.setItem('hsqst_display', displayMode); } catch (e) {}
  }
  function layout() {
    var cv = document.getElementById('game');
    var dpr = window.devicePixelRatio || 1;
    var padEl = document.getElementById('pad');
    var padH = (padEl && padEl.offsetHeight) || 186;
    var availW = Math.floor(Math.max(160, window.innerWidth - 12));
    var availH = Math.floor(Math.max(200, window.innerHeight - padH - 24));
    var cw, ch, r;
    if (displayMode === 'sharp') {
      var k = Math.floor(Math.min(availW * dpr / LOGICAL_W, availH * dpr / LOGICAL_H));
      if (k < 1) k = 1;
      r = k;
      cw = Math.round(LOGICAL_W * k / dpr);
      ch = Math.round(LOGICAL_H * k / dpr);
    } else {
      cw = Math.min(availW, Math.floor(availH * 3 / 4));
      cw -= cw % 3;
      if (cw < 120) cw = 120;
      ch = cw * 4 / 3;
      r = Math.max(1, Math.ceil(cw * dpr / LOGICAL_W));
    }
    var bw = LOGICAL_W * r, bh = LOGICAL_H * r;
    if (cv.width !== bw || cv.height !== bh) {
      cv.width = bw;   // 重设 backing 会清空画布，Java 侧下一帧全量重绘
      cv.height = bh;
    }
    cv.style.width = cw + 'px';
    cv.style.height = ch + 'px';
    cv.style.imageRendering = displayMode === 'sharp' ? 'pixelated' : 'auto';
    window.__gscaleX = r;
    window.__gscaleY = r;
    if (window.__resizeJava) window.__resizeJava(r, r);
  }
  function setDisplayMode(m) {
    displayMode = m;
    window.__dmode = m;
    try { localStorage.setItem('hsqst_display', m); } catch (e) {}
    var b = document.getElementById('btnQuality');
    if (b) b.textContent = m === 'sharp' ? '画质·锐利' : '画质·铺满';
    layout();
  }
  var qbtn = document.getElementById('btnQuality');
  if (qbtn) {
    qbtn.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      setDisplayMode(displayMode === 'sharp' ? 'fill' : 'sharp');
    });
    qbtn.textContent = displayMode === 'sharp' ? '画质·锐利' : '画质·铺满';
  }
  window.__dmode = displayMode;
  layout();
  layout();
  window.addEventListener('resize', layout);
  window.addEventListener('orientationchange', layout);
  if (window.visualViewport) window.visualViewport.addEventListener('resize', layout);

  // ---------- 按钮绑定（多指安全） ----------
  function bindButton(el, code) {
    var active = null; // pointerId
    el.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      if (active !== null) return;
      active = e.pointerId;
      el.classList.add('pressed');
      el.setPointerCapture(e.pointerId);
      pad(code, true);
    });
    function release(e) {
      if (active !== e.pointerId) return;
      active = null;
      el.classList.remove('pressed');
      pad(code, false);
    }
    el.addEventListener('pointerup', release);
    el.addEventListener('pointercancel', release);
    el.addEventListener('lostpointercapture', release);
  }

  document.querySelectorAll('[data-code]').forEach(function (el) {
    bindButton(el, parseInt(el.getAttribute('data-code'), 10));
  });

  // ---------- 游戏区触摸：战棋光标直控 + 九宫分区回退（全触屏核心） ----------
  var game = document.getElementById('game');
  var frame = document.getElementById('screenframe');
  var flashEl = document.getElementById('zoneflash');
  var touchMode = 'cursor'; // cursor=点哪儿光标走哪儿 | zones=屏幕九宫分区按键
  try {
    if (localStorage.getItem('hsqst_touch') === 'zones') touchMode = 'zones';
  } catch (e) {}
  window.__tmode = touchMode;
  var ZONES = [[49, 50, 51], [52, 53, 54], [55, 56, 57], [42, 48, 35]];
  var ZONE_LABEL = { 49: '1', 50: '2', 51: '3', 52: '4', 53: '5', 54: '6', 55: '7', 56: '8', 57: '9', 42: '*', 48: '0', 35: '#' };
  var TILE = 24; // 战场瓦片逻辑尺寸

  function gameCoords(e) {
    var r = game.getBoundingClientRect();
    var fx = (e.clientX - r.left) / r.width;
    var fy = (e.clientY - r.top) / r.height;
    fx = Math.max(0, Math.min(0.999, fx));
    fy = Math.max(0, Math.min(0.999, fy));
    return { x: Math.floor(fx * 240), y: Math.floor(fy * 320), fx: fx, fy: fy };
  }
  function zoneKey(g) {
    var col = Math.min(2, Math.floor(g.fx * 3));
    var row = Math.min(3, Math.floor(g.fy * 4));
    return ZONES[row][col];
  }
  var flashTimer = null;
  function flashZone(g, code) {
    if (!flashEl) return; // 旧缓存混用时防御
    var r = game.getBoundingClientRect();
    flashEl.style.display = 'flex';
    flashEl.style.left = Math.round(r.left + g.fx * r.width - r.width / 6) + 'px';
    flashEl.style.top = Math.round(r.top + g.fy * r.height - r.height / 8) + 'px';
    flashEl.style.width = Math.round(r.width / 3) + 'px';
    flashEl.style.height = Math.round(r.height / 4) + 'px';
    flashEl.textContent = ZONE_LABEL[code] || '';
    clearTimeout(flashTimer);
    flashTimer = setTimeout(function () { flashEl.style.display = 'none'; }, 240);
  }

  // 光标步进：把 (sx,sy) 格位移量翻译成交替的方向键点击
  var walk = null;
  function stopWalk() {
    if (walk) { clearInterval(walk.timer); walk = null; }
  }
  function press(code, ms) {
    pad(code, true);
    setTimeout(function () { pad(code, false); }, ms || 90);
  }
  function startWalk(sx, sy) {
    stopWalk();
    var left = { x: sx, y: sy };
    var count = 0;
    walk = { axis: Math.abs(sx) >= Math.abs(sy) ? 'x' : 'y', timer: 0 };
    function step() {
      if (left.x === 0 && left.y === 0) { stopWalk(); return; }
      var code;
      if (walk.axis === 'x' && left.x !== 0) {
        code = left.x > 0 ? -4 : -3;
        left.x += left.x > 0 ? -1 : 1;
      } else {
        code = left.y > 0 ? -2 : -1;
        left.y += left.y > 0 ? -1 : 1;
      }
      press(code, 70);
      if (++count > 40) { stopWalk(); return; }
      if (left.x !== 0 && left.y !== 0) walk.axis = walk.axis === 'x' ? 'y' : 'x';
    }
    walk.timer = setInterval(step, 160);
    step();
  }

  var tapPid = null, lpTimer = null;
  game.addEventListener('pointerdown', function (e) {
    e.preventDefault();
    if (tapPid !== null) return;
    tapPid = e.pointerId;
    var g = gameCoords(e);
    if (window.__touchdebug) console.log('[touch] down g=' + g.x + ',' + g.y + ' mode=' + touchMode + ' cur=' + JSON.stringify(window.__cursor || null));
    clearTimeout(lpTimer);
    lpTimer = setTimeout(function () {
      // 长按 550ms = 取消/返回
      stopWalk();
      press(-7);
    }, 550);
    if (touchMode !== 'cursor') {
      var k = zoneKey(g);
      press(k);
      flashZone(g, k);
      return;
    }
    var cur = window.__cursor;
    // 新鲜度=光标画在最近一帧里（游戏是脏矩形重绘，对话静止时可能很久不重绘）
    var fresh = cur && cur.w > 4 && (!window.__lastpaint || cur.t >= window.__lastpaint - 20);
    if (window.__touchdebug) console.log('[touch] fresh=' + fresh + ' delta=' + (cur ? Math.round((g.x - cur.x - cur.w/2)) + ',' + Math.round((g.y - cur.y - cur.h/2)) : '-'));
    if (fresh) {
      var cx = cur.x + cur.w / 2, cy = cur.y + cur.h / 2;
      var sx = Math.round((g.x - cx) / TILE);
      var sy = Math.round((g.y - cy) / TILE);
      if (sx === 0 && sy === 0) {
        clearTimeout(lpTimer);
        press(-5); // 点在光标上 = 确认
      } else {
        startWalk(sx, sy);
      }
    } else {
      // 无光标帧（对话/菜单）：点击 = 确认/推进
      clearTimeout(lpTimer);
      press(-5);
    }
  });
  function tapUp(e) {
    if (tapPid !== e.pointerId) return;
    tapPid = null;
    clearTimeout(lpTimer);
  }
  game.addEventListener('pointerup', tapUp);
  game.addEventListener('pointercancel', tapUp);

  // ---------- 键盘收起（纯触屏） ----------
  function setPadHidden(h) {
    document.body.classList.toggle('padhidden', h);
    try { localStorage.setItem('hsqst_padhidden', h ? '1' : '0'); } catch (e2) {}
    layout();
    layout();
  }
  var padHideBtn = document.getElementById('btnPadHide');
  if (padHideBtn) {
    padHideBtn.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      setPadHidden(true);
    });
  }
  var floatPadBtn = document.getElementById('floatPad');
  if (floatPadBtn) {
    floatPadBtn.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      setPadHidden(false);
    });
  }
  try {
    if (localStorage.getItem('hsqst_padhidden') === '1') {
      document.body.classList.add('padhidden');
    }
  } catch (e) {}

  // ---------- 关卡包切换（原版/扩展双构建） ----------
  var packBtn = document.getElementById('btnPack');
  if (packBtn) {
    var curPack = 'classic';
    try { curPack = localStorage.getItem('hsqst_pak') === 'custom' ? 'custom' : 'classic'; } catch (e) {}
    packBtn.textContent = curPack === 'custom' ? '关卡包·扩展' : '关卡包·原版';
    window.__packlabel = curPack;
    packBtn.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      var next = curPack === 'custom' ? 'classic' : 'custom';
      try { localStorage.setItem('hsqst_pak', next); } catch (e2) {}
      location.reload();
    });
  }

  // ---------- 触摸模式切换 ----------
  var touchBtn = document.getElementById('btnTouch');
  if (touchBtn) {
    touchBtn.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      touchMode = touchMode === 'cursor' ? 'zones' : 'cursor';
      window.__tmode = touchMode;
      touchBtn.textContent = touchMode === 'cursor' ? '触控·直控' : '触控·分区';
      try { localStorage.setItem('hsqst_touch', touchMode); } catch (e2) {}
    });
    touchBtn.textContent = touchMode === 'cursor' ? '触控·直控' : '触控·分区';
  }

  // ---------- 桌面调试键盘 ----------
  var KEYMAP = {
    'ArrowUp': -1, 'ArrowDown': -2, 'ArrowLeft': -3, 'ArrowRight': -4,
    'KeyW': -1, 'KeyS': -2, 'KeyA': -3, 'KeyD': -4,
    'Enter': -5, 'Space': -5,
    'KeyQ': -6, 'ShiftLeft': -6,
    'Escape': -7, 'KeyE': -7, 'Backspace': -7,
    'Digit1': 49, 'Digit2': 50, 'Digit3': 51, 'Digit4': 52, 'Digit5': 53,
    'Digit6': 54, 'Digit7': 55, 'Digit8': 56, 'Digit9': 57, 'Digit0': 48,
    'Numpad1': 49, 'Numpad2': 50, 'Numpad3': 51, 'Numpad4': 52, 'Numpad5': 53,
    'Numpad6': 54, 'Numpad7': 55, 'Numpad8': 56, 'Numpad9': 57, 'Numpad0': 48,
    'NumpadMultiply': 42, 'NumpadAdd': 35
  };
  var held = {};
  window.addEventListener('keydown', function (e) {
    var code = KEYMAP[e.code];
    if (code === undefined) return;
    e.preventDefault();
    if (held[e.code]) return;
    held[e.code] = true;
    pad(code, true);
  });
  window.addEventListener('keyup', function (e) {
    var code = KEYMAP[e.code];
    if (code === undefined) return;
    e.preventDefault();
    held[e.code] = false;
    pad(code, false);
  });

  // ---------- 防手势（双击缩放/长按菜单/橡皮筋） ----------
  document.addEventListener('dblclick', function (e) { e.preventDefault(); }, { passive: false });
  document.addEventListener('contextmenu', function (e) { e.preventDefault(); });
  document.addEventListener('touchmove', function (e) { e.preventDefault(); }, { passive: false });
  document.addEventListener('gesturestart', function (e) { e.preventDefault(); });
})();

// ---------- 布局探针（?debug=1 时显示） ----------
if (location.search.indexOf('debug=1') >= 0) {
  window.__touchdebug = true;
  window.addEventListener('load', function () {
    setTimeout(function () {
      var np = document.getElementById('numpad');
      var mid = document.getElementById('midrow');
      var d = document.createElement('div');
      d.style.cssText = 'position:fixed;top:0;left:0;background:#fff;color:#000;font-size:10px;z-index:99;padding:2px;white-space:pre;';
      d.textContent = 'iw=' + window.innerWidth + ' ih=' + window.innerHeight
        + '\nbody=' + document.body.offsetWidth
        + '\npad=' + document.getElementById('pad').offsetWidth
        + ' padH=' + document.getElementById('pad').offsetHeight
        + '\nmid=' + mid.offsetWidth + ' np=' + np.offsetWidth
        + '\ngrid=' + getComputedStyle(np).gridTemplateColumns
        + '\nbtn=' + np.querySelector('.nbtn').offsetWidth
        + '\ncv=' + document.getElementById('game').width + 'x' + document.getElementById('game').height
        + '\ncss=' + document.getElementById('game').style.width + ' x ' + document.getElementById('game').style.height
        + '\ngs=' + window.__gscaleX + 'x' + window.__gscaleY + ' dpr=' + window.devicePixelRatio
        + '\nmode=' + window.__dmode + ' touch=' + window.__tmode
        + '\npadhidden=' + document.body.classList.contains('padhidden')
        + '\ncursor=' + JSON.stringify(window.__cursor || null);
      document.body.appendChild(d);
      // 像素探针：直接读画布位图，区分"渲染黑屏"与"截图伪影"
      setInterval(function () {
        var c = document.getElementById('game');
        try {
          var px = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
          var n = 0, total = 0;
          for (var i = 0; i < px.length; i += 4013 * 4) {
            total++;
            if (px[i] | px[i + 1] | px[i + 2]) n++;
          }
          console.log('[probe] cv=' + c.width + 'x' + c.height + ' nonblack=' + n + '/' + total
            + ' cursor=' + JSON.stringify(window.__cursor || null) + ' tmode=' + window.__tmode);
        } catch (e) {
          console.log('[probe] err ' + e);
        }
      }, 1500);
    }, 500);
  });
}

// ---------- 自动按键驱动（?autotap=-5@2000,P120,160@5000 无头测试用） ----------
(function () {
  var m = location.search.match(/autotap=([^&]+)/);
  if (!m) return;
  m[1].split(',').forEach(function (item) {
    var mm = item.match(/^(-?\d+)@(\d+)$/);
    if (mm) {
      var code = parseInt(mm[1], 10);
      var delay = parseInt(mm[2], 10);
      setTimeout(function () {
        window.__pad(code, true);
        setTimeout(function () { window.__pad(code, false); }, 120);
      }, delay);
      return;
    }
    var pm = item.match(/^P(\d+)x(\d+)@(\d+)$/); // P=120x160@delay 模拟游戏区触摸
    if (pm) {
      var gx = parseInt(pm[1], 10), gy = parseInt(pm[2], 10), pd = parseInt(pm[3], 10);
      setTimeout(function () {
        var cv = document.getElementById('game');
        var r = cv.getBoundingClientRect();
        var cx = r.left + (gx + 0.5) / 240 * r.width;
        var cy = r.top + (gy + 0.5) / 320 * r.height;
        cv.dispatchEvent(new PointerEvent('pointerdown', { clientX: cx, clientY: cy, pointerId: 777, bubbles: true }));
        setTimeout(function () {
          cv.dispatchEvent(new PointerEvent('pointerup', { clientX: cx, clientY: cy, pointerId: 777, bubbles: true }));
        }, 80);
      }, pd);
    }
  });
})();

// ---------- PWA Service Worker ----------
if ('serviceWorker' in navigator && location.protocol === 'https:' || location.hostname === 'localhost') {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('./sw.js').catch(function (e) { /* 忽略 */ });
  });
}
