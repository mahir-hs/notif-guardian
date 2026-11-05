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
