/* 虚拟按键桥：DOM 按键/游戏区点击/物理键盘 -> window.__pad -> TeaVM(Java HostInput) */
(function () {
  'use strict';

  var pending = window.__padJava; // TeaVM 启动后注入
  function pad(code, down) {
    if (window.__padJava) window.__padJava(code, down);
  }
  window.__pad = pad; // 供 autotap 等外部驱动使用

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

  // ---------- 游戏区点击 = 确认键（v1 简单映射） ----------
  var game = document.getElementById('game');
  var tapId = null;
  game.addEventListener('pointerdown', function (e) {
    e.preventDefault();
    if (tapId !== null) return;
    tapId = e.pointerId;
    pad(-5, true);
  });
  function tapRelease(e) {
    if (tapId !== e.pointerId) return;
    tapId = null;
    pad(-5, false);
  }
  game.addEventListener('pointerup', tapRelease);
  game.addEventListener('pointercancel', tapRelease);

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
        + '\nmode=' + window.__dmode;
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
          console.log('[probe] cv=' + c.width + 'x' + c.height + ' nonblack=' + n + '/' + total);
        } catch (e) {
          console.log('[probe] err ' + e);
        }
      }, 1500);
    }, 500);
  });
}

// ---------- 自动按键驱动（?autotap=-5@2000,-5@5000 无头测试用） ----------
(function () {
  var m = location.search.match(/autotap=([^&]+)/);
  if (!m) return;
  m[1].split(',').forEach(function (item) {
    var mm = item.match(/^(-?\d+)@(\d+)$/);
    if (!mm) return;
    var code = parseInt(mm[1], 10);
    var delay = parseInt(mm[2], 10);
    setTimeout(function () {
      window.__pad(code, true);
      setTimeout(function () { window.__pad(code, false); }, 120);
    }, delay);
  });
})();

// ---------- PWA Service Worker ----------
if ('serviceWorker' in navigator && location.protocol === 'https:' || location.hostname === 'localhost') {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('./sw.js').catch(function (e) { /* 忽略 */ });
  });
}
