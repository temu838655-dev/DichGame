/* Dịch Game - native TV browser receiver (Tizen / webOS).
 * Requires phone server /tv-pair and /tv-poll on port 8780.
 * No external CDN, trackers, remote JavaScript or embedded secrets.
 */
(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var status = $('status');
  var subtitle = $('subtitle');
  var panel = $('panel');
  var ipField = $('phoneIp');
  var pinField = $('pairCode');
  var connect = $('connect');
  var disconnect = $('disconnect');
  var details = $('details');
  var hdmiSelect = $('hdmiSelect');
  var hdmiButton = $('hdmiButton');
  var isSamsung = !!(window.tizen && window.tizen.tvwindow);
  var remote = '';
  var token = '';
  var seq = -1;
  var req = null;
  var retry = null;
  var running = false;
  var activeSource = '';

  function report(message) { status.textContent = message; }
  function endpoint(path) { return remote + path; }
  function safeIp(value) {
    var s = String(value || '').trim();
    // LAN IPv4 only; prevents arbitrary URLs/injection from the input field.
    var m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(s);
    if (!m) return '';
    for (var i = 1; i < 5; i++) if (Number(m[i]) > 255) return '';
    return s;
  }
  function send(path, timeout, done) {
    var x = new XMLHttpRequest();
    x.open('GET', endpoint(path), true);
    x.timeout = timeout;
    x.setRequestHeader('Accept', 'application/json');
    x.onload = function () {
      var data = null;
      try { data = JSON.parse(x.responseText); } catch (ignored) {}
      done(x.status, data);
    };
    x.onerror = x.ontimeout = function () { done(0, null); };
    x.send(null);
    return x;
  }
  function clearTimer() { if (retry) clearTimeout(retry); retry = null; }
  function stop() {
    running = false;
    clearTimer();
    if (req) { try { req.abort(); } catch (ignored) {} req = null; }
  }
  function showPanel(message) {
    panel.style.display = 'block';
    if (message) report(message);
    $('phoneIp').focus();
  }
  function hidePanel() { panel.style.display = 'none'; }
  function failed(message, wait) {
    report(message);
    clearTimer();
    if (running) retry = setTimeout(poll, wait || 1500);
  }
  function poll() {
    if (!running || !token || req) return;
    req = send('/tv-poll?since=' + encodeURIComponent(seq) +
      '&token=' + encodeURIComponent(token) + '&_=' + Date.now(), 28500, function (code, data) {
      req = null;
      if (!running) return;
      if (code === 403) {
        stop();
        token = '';
        subtitle.textContent = '';
        try { localStorage.removeItem('dg-receiver-token'); } catch (ignored) {}
        showPanel('Phiên ghép TV đã hết. Nhập mã mới trên điện thoại.');
        return;
      }
      if (code !== 200 || !data || typeof data.seq !== 'number' || typeof data.text !== 'string') {
        failed('Đứt kết nối đến điện thoại. Đang thử lại…', 1600);
        return;
      }
      seq = data.seq;
      subtitle.textContent = data.text; // Never interpret video subtitles as HTML
      details.textContent = data.text ? 'Đang nhận phụ đề trực tiếp' : 'Kết nối thành công · chờ phụ đề';
      report('TV đã ghép với điện thoại');
      retry = setTimeout(poll, 40);
    });
  }
  function attach(host, newToken) {
    stop();
    remote = 'http://' + host + ':8780';
    token = newToken;
    seq = -1;
    running = true;
    hidePanel();
    details.textContent = 'Đã ghép · chờ phụ đề từ điện thoại';
    poll();
  }
  function pair() {
    var host = safeIp(ipField.value);
    var pin = pinField.value.replace(/\s+/g, '');
    if (!host) { report('Nhập IP điện thoại hợp lệ (ví dụ 192.168.1.12).'); return; }
    if (!/^\d{8}$/.test(pin)) { report('Mã ghép phải có đúng 8 chữ số.'); return; }
    remote = 'http://' + host + ':8780';
    report('Đang ghép với điện thoại…');
    send('/tv-pair?code=' + encodeURIComponent(pin), 6000, function (code, data) {
      if (code !== 200 || !data || !/^[0-9a-f]{48}$/.test(data.token || '')) {
        report(code === 403 ? 'Mã ghép không hợp lệ hoặc đã thử quá nhiều lần.' :
          'Không kết nối được. Hãy bật trang phụ đề trên điện thoại và dùng cùng Wi-Fi.');
        return;
      }
      try {
        localStorage.setItem('dg-receiver-host', host);
        localStorage.setItem('dg-receiver-token', data.token);
      } catch (ignored) {}
      pinField.value = '';
      attach(host, data.token);
    });
  }

  function showHdmiInputOptions() {
    if (!isSamsung || !window.tizen.systeminfo) return;
    try {
      tizen.systeminfo.getPropertyValue('VIDEOSOURCE', function (sources) {
        var available = sources && sources.connected ? sources.connected : [];
        var connected = [];
        for (var i = 0; i < available.length; i++) {
          if (available[i].type === 'HDMI') connected.push(available[i]);
        }
        hdmiSelect.innerHTML = '';
        if (!connected.length) {
          hdmiButton.style.display = 'none';
          report('Không phát hiện HDMI kết nối. Bật PS5 và kiểm tra cáp HDMI.');
          return;
        }
        hdmiButton.style.display = 'inline-block';
        for (var j = 0; j < connected.length; j++) {
          var o = document.createElement('option');
          o.value = String(j);
          o.textContent = 'HDMI ' + (connected[j].number || (j + 1));
          hdmiSelect.appendChild(o);
        }
        hdmiButton.onclick = function () {
          var selected = connected[Number(hdmiSelect.value) || 0];
          if (!selected) return;
          try {
            tizen.tvwindow.setSource(selected, function () {
              activeSource = String(selected.number || '');
              report('Đã chọn HDMI ' + activeSource);
            }, function (err) {
              report('TV không chuyển nguồn HDMI: ' + (err && err.message ? err.message : 'không hỗ trợ'));
            }, 'MAIN');
          } catch (error) { report('Không đổi được HDMI: ' + error.message); }
        };
        if (connected.length === 1) hdmiButton.click();
      }, function () { report('Không đọc được danh sách HDMI.'); });
    } catch (error) { report('VIDEOSOURCE không khả dụng trên TV này.'); }
  }
  function startTvWindow() {
    if (!isSamsung) return;
    try {
      tizen.tvwindow.show(function () {
        report('Đã bật lớp hình HDMI bằng TVWindow');
        showHdmiInputOptions();
      }, function (error) {
        report('TVWindow không hoạt động: ' + (error && error.message ? error.message : 'không được cấp quyền'));
      }, ['0%', '0%', '100%', '100%'], 'MAIN', 'BEHIND');
    } catch (error) { report('Không thể mở PiP: ' + error.message); }
  }

  connect.onclick = pair;
  disconnect.onclick = function () {
    stop();
    token = '';
    seq = -1;
    subtitle.textContent = '';
    try { localStorage.removeItem('dg-receiver-token'); } catch (ignored) {}
    showPanel('Đã ngắt. Nhập mã mới để ghép TV.');
  };
  document.addEventListener('keydown', function (event) {
    if (event.keyCode === 38 && document.activeElement !== ipField &&
        document.activeElement !== pinField) {
      // Mũi tên LÊN trên remote để mở lại cài đặt.
      showPanel('Cài đặt kết nối TV');
    }
  });
  if (window.addEventListener) {
    window.addEventListener('online', function () { if (running && !req) poll(); });
  }
  try {
    var savedHost = safeIp(localStorage.getItem('dg-receiver-host'));
    var savedToken = localStorage.getItem('dg-receiver-token') || '';
    if (savedHost) ipField.value = savedHost;
    if (savedHost && /^[0-9a-f]{48}$/.test(savedToken)) attach(savedHost, savedToken);
    else showPanel('Mở Dịch Game trên điện thoại → Phụ đề trên TV → Bật trang web phụ đề.');
  } catch (ignored) { showPanel('Nhập IP điện thoại và mã ghép.'); }

  if (isSamsung) startTvWindow();
  else { hdmiSelect.style.display = 'none'; hdmiButton.style.display = 'none'; }
}());
