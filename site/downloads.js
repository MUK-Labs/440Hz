(function (root) {
  'use strict';

  const repository = 'MUK-Labs/440Hz';
  const releasesUrl = 'https://api.github.com/repos/' + repository + '/releases?per_page=100';
  const fallback = [
    { version: '0.4.0', url: '440-v0.4.0-test.apk' },
    { version: '0.3.0', url: '440-v0.3.0-test.apk' }
  ];

  function versionParts(version) {
    if (typeof version !== 'string' || !/^\d+\.\d+\.\d+$/.test(version)) return null;
    const parts = version.split('.').map(Number);
    return parts.every(Number.isSafeInteger) ? parts : null;
  }

  function newestFirst(a, b) {
    const aa = versionParts(a.version);
    const bb = versionParts(b.version);
    for (let i = 0; i < 3; i++) {
      if (aa[i] !== bb[i]) return bb[i] - aa[i];
    }
    return 0;
  }

  function localVersions(data) {
    if (!data || !Array.isArray(data.versions)) return [];
    return data.versions.filter(function (entry) {
      return entry && versionParts(entry.version) &&
        entry.url === '440-v' + entry.version + '-test.apk';
    }).map(function (entry) {
      return { version: entry.version, url: entry.url, sizeBytes: entry.sizeBytes };
    });
  }

  function releaseVersions(releases) {
    if (!Array.isArray(releases)) return [];
    return releases.flatMap(function (release) {
      if (!release || release.draft || release.prerelease ||
          typeof release.tag_name !== 'string' || !/^v\d+\.\d+\.\d+$/.test(release.tag_name)) return [];
      const version = release.tag_name.slice(1);
      if (!versionParts(version) || !Array.isArray(release.assets)) return [];
      const filename = '440-v' + version + '-test.apk';
      const expectedUrl = 'https://github.com/' + repository + '/releases/download/v' + version + '/' + filename;
      const asset = release.assets.find(function (item) {
        return item && item.name === filename && item.browser_download_url === expectedUrl && item.size > 0;
      });
      return asset ? [{ version: version, url: asset.browser_download_url, sizeBytes: asset.size }] : [];
    });
  }

  // Local copies win a duplicate version, preserving their original signing key.
  function mergeVersions(local, remote) {
    const byVersion = new Map();
    remote.concat(local).forEach(function (entry) {
      if (entry && versionParts(entry.version)) byVersion.set(entry.version, entry);
    });
    return Array.from(byVersion.values()).sort(newestFirst);
  }

  function formatSize(bytes) {
    return Number.isFinite(bytes) && bytes > 0 ? (bytes / 1000000).toFixed(1) + ' MB' : '';
  }

  function init(document, fetcher) {
    const select = document.getElementById('version-select');
    const versionLabel = document.getElementById('selected-version');
    const status = document.getElementById('download-status');
    if (!select || !versionLabel || !status) return;
    const links = document.querySelectorAll('[data-apk-download]');
    let versions = fallback.slice();
    let userSelected = false;
    let checkFailed = false;
    let checking = true;

    function updateSelection() {
      const entry = versions.find(function (item) { return item.version === select.value; }) || versions[0];
      select.value = entry.version;
      const size = formatSize(entry.sizeBytes);
      versionLabel.textContent = 'v' + entry.version + (size ? ', ' + size : '');
      links.forEach(function (link) {
        link.href = entry.url;
        link.setAttribute('download', '440-v' + entry.version + '-test.apk');
        link.setAttribute('aria-label', 'Download 440 v' + entry.version + ' test APK');
      });
      const selected = 'Selected v' + entry.version + '. ';
      status.textContent = selected + (checking ? 'Checking for newer versions…' :
        checkFailed ? 'Could not check for newer versions. The listed downloads are still available.' :
        'Newest available: v' + versions[0].version + '. Updates are installed manually.');
    }

    function render() {
      const previous = select.value;
      select.replaceChildren();
      versions.forEach(function (entry, index) {
        const option = document.createElement('option');
        option.value = entry.version;
        option.textContent = 'v' + entry.version + (index === 0 ? ' · Newest' : '');
        select.appendChild(option);
      });
      select.value = userSelected && versions.some(function (entry) { return entry.version === previous; })
        ? previous : versions[0].version;
      select.disabled = false;
      updateSelection();
    }

    select.addEventListener('change', function () { userSelected = true; updateSelection(); });
    render();
    function json(url) {
      const controller = new AbortController();
      const timer = setTimeout(function () { controller.abort(); }, 8000);
      return fetcher(url, { cache: 'no-store', signal: controller.signal, credentials: 'omit' })
        .then(function (response) {
          if (!response.ok) throw new Error('Download list unavailable');
          return response.json();
        }).finally(function () { clearTimeout(timer); });
    }

    return json('versions.json').then(function (data) {
      const entries = localVersions(data);
      if (!entries.length) throw new Error('No bundled versions');
      versions = mergeVersions(fallback, entries);
      // Prefer manifest sizes over the minimal fallback records.
      versions = mergeVersions(entries, versions);
      render();
    }).catch(function () {
      // Keep the inline bundled downloads if the local manifest is unavailable.
    }).then(function () {
      return json(releasesUrl).then(function (data) {
        if (!Array.isArray(data)) throw new Error('Invalid release list');
        versions = mergeVersions(versions, releaseVersions(data));
      }).catch(function () { checkFailed = true; });
    }).finally(function () { checking = false; render(); });
  }

  const api = { versionParts, newestFirst, localVersions, releaseVersions, mergeVersions, formatSize, init };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root.document && typeof root.fetch === 'function') init(root.document, root.fetch.bind(root));
})(typeof globalThis !== 'undefined' ? globalThis : this);
