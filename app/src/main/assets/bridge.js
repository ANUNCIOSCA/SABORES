/* Puente Web -> Android para app.247hrs.net.
 * En un WebView, las descargas por <a download href="blob:..."> y navigator.share({files})
 * no funcionan; aquí se interceptan y se entregan a la app nativa (RCAndroid). */
(function () {
  if (window.__rcBridge || !window.RCAndroid) return;
  window.__rcBridge = true;
  var A = window.RCAndroid;

  function toB64(blob, cb) {
    var r = new FileReader();
    r.onload = function () { var s = String(r.result); cb(s.substring(s.indexOf(',') + 1)); };
    r.onerror = function () { A.toast('No se pudo leer el archivo'); };
    r.readAsDataURL(blob);
  }

  function grab(href, name) {
    fetch(href).then(function (r) { return r.blob(); }).then(function (b) {
      toB64(b, function (d) { A.saveFile(d, name || 'archivo', b.type || ''); });
    }).catch(function (e) { A.toast('Error al descargar: ' + e.message); });
  }
  window.__rcGrab = grab;

  function handle(a) {
    var h = a && a.href ? String(a.href) : '';
    if (!/^(blob|data):/i.test(h)) return false;
    grab(h, a.getAttribute('download') || '');
    return true;
  }

  var oClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (handle(this)) return;
    return oClick.apply(this, arguments);
  };
  var oDispatch = HTMLAnchorElement.prototype.dispatchEvent || EventTarget.prototype.dispatchEvent;
  HTMLAnchorElement.prototype.dispatchEvent = function (ev) {
    if (ev && ev.type === 'click' && handle(this)) return false;
    return oDispatch.call(this, ev);
  };
  document.addEventListener('click', function (ev) {
    var a = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
    if (a && handle(a)) { ev.preventDefault(); ev.stopImmediatePropagation(); }
  }, true);

  var oOpen = window.open;
  window.open = function (u) {
    if (typeof u === 'string' && /^(blob|data):/i.test(u)) { grab(u, 'archivo'); return null; }
    return oOpen.apply(window, arguments);
  };

  function share(d) {
    return new Promise(function (ok) {
      d = d || {};
      if (d.files && d.files.length) {
        var f = d.files[0];
        toB64(f, function (b) { A.shareFile(b, f.name || 'archivo', f.type || '', d.title || '', d.text || ''); ok(); });
      } else {
        A.shareText(d.title || '', [d.text, d.url].filter(Boolean).join('\n'));
        ok();
      }
    });
  }
  function canShare(d) { return !!(d && ((d.files && d.files.length) || d.text || d.url)); }
  try {
    Object.defineProperty(navigator, 'share', { value: share, configurable: true, writable: true });
    Object.defineProperty(navigator, 'canShare', { value: canShare, configurable: true, writable: true });
  } catch (e) { navigator.share = share; navigator.canShare = canShare; }
})();
