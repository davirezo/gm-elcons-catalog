(function () {
  'use strict';
  function csrf() {
    var m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return m ? decodeURIComponent(m[1]) : '';
  }
  var form = document.getElementById('login-form');
  var err = document.getElementById('login-error');
  var btn = document.getElementById('login-btn');
  form.addEventListener('submit', async function (ev) {
    ev.preventDefault();
    err.textContent = '';
    btn.disabled = true;
    btn.textContent = 'Entrando…';
    try {
      var body = new URLSearchParams();
      body.set('username', form.username.value.trim());
      body.set('password', form.password.value);
      var res = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': csrf() },
        body: body.toString()
      });
      if (res.ok) {
        window.location.href = '/';
        return;
      }
      err.textContent = res.status === 401 ? 'Usuário ou senha inválidos.' : 'Não foi possível entrar (código ' + res.status + ').';
    } catch (e) {
      err.textContent = 'Falha de conexão com o servidor.';
    }
    btn.disabled = false;
    btn.textContent = 'Entrar';
  });
})();
