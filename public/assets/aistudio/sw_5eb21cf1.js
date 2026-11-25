/* X-Ray Mirror offline runtime cache. External MediaPipe/WASM/model assets are
   cached as they are fetched during the first successful online launch. */
const SHELL_CACHE = 'xray-shell-v1';
const RUNTIME_CACHE = 'xray-runtime-v1';
const SHELL = ['./', './xray-mirror-v3.html', './manifest.webmanifest', './icon.svg', './icon-192.png', './icon-512.png'];
self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL_CACHE);
    for (const path of SHELL) {
      try { await cache.add(new Request(new URL(path, self.registration.scope), {cache:'reload'})); }
      catch (_) { /* root index is optional; app html is the primary entry */ }
    }
    await self.skipWaiting();
  })());
});
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter(n => n.startsWith('xray-') && ![SHELL_CACHE,RUNTIME_CACHE].includes(n)).map(n => caches.delete(n)));
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const sameOrigin = url.origin === self.location.origin;
  // Cache-first for remote engine/model/font assets after they have been fetched once.
  if (!sameOrigin) {
    event.respondWith((async () => {
      const cache = await caches.open(RUNTIME_CACHE);
      const cached = await cache.match(req, {ignoreVary:true});
      if (cached) return cached;
      try {
        const response = await fetch(req);
        if (response && response.ok && (response.type === 'cors' || response.type === 'basic')) {
          try { await cache.put(req, response.clone()); } catch (_) {}
        }
        return response;
      } catch (err) {
        return new Response('Offline asset unavailable. Connect once to download this model/runtime file.', {status:503, headers:{'Content-Type':'text/plain; charset=utf-8'}});
      }
    })());
    return;
  }
  // App shell: serve cached HTML offline; other same-origin files use network-first.
  if (req.mode === 'navigate' || /\.(?:html|webmanifest|svg|js|css)$/.test(url.pathname)) {
    event.respondWith((async () => {
      const cache = await caches.open(SHELL_CACHE);
      try {
        const response = await fetch(req);
        if (response.ok) { try { await cache.put(req, response.clone()); } catch (_) {} }
        return response;
      } catch (_) {
        return (await cache.match(req)) || (await cache.match(new URL('./xray-mirror-v3.html', self.registration.scope))) || Response.error();
      }
    })());
  }
});
