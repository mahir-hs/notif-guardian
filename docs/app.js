// Shared by every language page. Per-page text comes from window.T and window.PAGE (set inline by site/build.py).
(function () {
  const T = window.T || {};
  const PAGE = window.PAGE || { lang: 'en', base: '', langs: {} };
  const tr = s => (T.dyn && T.dyn[s]) || s;

  // Language picker: remember the choice so the root page stops auto-redirecting
  const picker = document.getElementById('lang-select');
  if (picker) {
    picker.addEventListener('change', () => {
      try { localStorage.setItem('ng-lang', picker.value); } catch (e) {}
      location.href = PAGE.langs[picker.value];
    });
  }

  // Delivery path toggle
  const path = document.getElementById('path');
  const verdict = document.getElementById('verdict');
  document.querySelectorAll('.toggle button').forEach(btn => {
    btn.addEventListener('click', () => {
      const mode = btn.dataset.set;
      path.dataset.mode = mode;
      document.querySelectorAll('.toggle button').forEach(b => b.setAttribute('aria-pressed', String(b === btn)));
      path.querySelectorAll('.state[data-' + mode + ']').forEach(s => { s.textContent = s.dataset[mode]; });
      verdict.textContent = verdict.dataset[mode];
    });
  });

  // Donation options come from support.json, shared with the app. English text there is
  // replaced by the page's translation when T.dyn has one.
  fetch(PAGE.base + 'support.json', { cache: 'no-cache' })
    .then(r => r.ok ? r.json() : Promise.reject(r.status))
    .then(info => {
      if (info.message) document.getElementById('support-message').textContent = tr(info.message);
      if (info.elsewhere) document.getElementById('donate-elsewhere').textContent = tr(info.elsewhere);
      const groups = document.getElementById('donate-groups');
      const regions = info.regions || {};
      const lists = {};
      const listFor = code => {
        if (lists[code]) return lists[code];
        const region = document.createElement('div');
        region.className = 'region';
        const r = regions[code];
        if (r) {
          const head = document.createElement('div');
          head.className = 'region-head';
          const flag = document.createElement('img');
          flag.src = PAGE.base + 'flags/' + code + '.svg'; flag.alt = '';
          const name = document.createElement('strong');
          name.textContent = (T.donate_in || 'In {name}').replace('{name}', tr(r.name));
          head.append(flag, name);
          region.appendChild(head);
          if (r.about) { const a = document.createElement('p'); a.className = 'region-about'; a.textContent = tr(r.about); region.appendChild(a); }
        }
        const ul = document.createElement('ul');
        ul.className = 'donate';
        region.appendChild(ul);
        groups.appendChild(region);
        return (lists[code] = ul);
      };
      (info.methods || []).forEach(m => {
        const list = listFor(m.region || '');
        const li = document.createElement('li');
        const what = document.createElement('div');
        what.className = 'what';
        const name = document.createElement('strong');
        name.textContent = m.name;
        what.appendChild(name);
        if (m.value) { const v = document.createElement('span'); v.className = 'value'; v.textContent = m.value; what.appendChild(v); }
        if (m.note) { const n = document.createElement('div'); n.className = 'note'; n.textContent = tr(m.note); what.appendChild(n); }
        li.appendChild(what);
        if (m.url) {
          const a = document.createElement('a');
          a.className = 'go'; a.href = m.url; a.rel = 'noopener'; a.textContent = T.open || 'Open';
          li.appendChild(a);
        } else if (m.value) {
          const b = document.createElement('button');
          b.type = 'button'; b.textContent = T.copy || 'Copy';
          b.addEventListener('click', () => navigator.clipboard.writeText(m.value).then(() => { b.textContent = T.copied || 'Copied'; }));
          li.appendChild(b);
        }
        list.appendChild(li);
      });
    })
    .catch(() => {});

  // Point the download buttons straight at the latest release's APKs
  fetch('https://api.github.com/repos/mahir-hs/notif-guardian/releases/latest')
    .then(r => r.ok ? r.json() : Promise.reject(r.status))
    .then(release => {
      const find = prefix => release.assets.find(a => a.name.startsWith(prefix) && a.name.endsWith('.apk'));
      const guardian = find('NotifGuardian-');
      const helper = find('GuardianHelper-');
      if (guardian) document.getElementById('dl-guardian').href = guardian.browser_download_url;
      if (helper) document.getElementById('dl-helper').href = helper.browser_download_url;
      let date;
      try {
        date = new Date(release.published_at).toLocaleDateString(PAGE.lang, { year: 'numeric', month: 'long', day: 'numeric' });
      } catch (e) {
        date = new Date(release.published_at).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
      }
      document.getElementById('release-meta').textContent =
        (T.release_meta || 'Version {version}, released {date}. Free and open source. No root, no computer.')
          .replace('{version}', release.tag_name.replace(/^v/, '')).replace('{date}', date);
    })
    .catch(() => { /* buttons keep pointing at the releases page */ });
})();
