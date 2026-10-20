/* EasyVacc PWA
   - Guarda paginas e assets visitados para uso offline.
   - Mantem POSTs fora do service worker; a fila de sincronizacao fica em
     offline-sync.js, no IndexedDB do navegador. */

const STATIC_CACHE = 'easyvacc-static-v7';
const PAGE_CACHE = 'easyvacc-pages-v1';
const RUNTIME_CACHE = 'easyvacc-runtime-v1';
const PRECACHE_URLS = [
  '/',
  '/manifest.webmanifest',
  '/static/vacinas/easyvacc.png',
  '/static/vacinas/icon-192.png',
  '/static/vacinas/icon-512.png',
  '/static/vacinas/offline-sync.js',
  '/static/vacinas/pwa-install.css',
  '/static/vacinas/pwa-install.js',
  '/static/vacinas/share-posto.js',
];

function sameOrigin(url) {
  return url.origin === self.location.origin;
}

function shouldSkipSameOrigin(url) {
  return (
    url.pathname.startsWith('/admin/') ||
    url.pathname.startsWith('/logout/') ||
    url.pathname.startsWith('/exportar-csv/')
  );
}

function cacheableResponse(response) {
  return response && (
    response.status === 200 ||
    response.type === 'opaque'
  );
}

function cacheFirst(request, cacheName) {
  return caches.match(request).then((cached) => {
    if (cached) return cached;
    return fetch(request).then((response) => {
      if (cacheableResponse(response)) {
        const clone = response.clone();
        caches.open(cacheName).then((cache) => cache.put(request, clone));
      }
      return response;
    });
  });
}

function networkFirst(request, cacheName, fallbackUrl) {
  return fetch(request)
    .then((response) => {
      if (cacheableResponse(response)) {
        const clone = response.clone();
        caches.open(cacheName).then((cache) => cache.put(request, clone));
      }
      return response;
    })
    .catch(() =>
      caches.match(request).then((cached) => {
        if (cached) return cached;
        if (fallbackUrl) return caches.match(fallbackUrl);
        return Response.error();
      }),
    );
}

function staleWhileRevalidate(request, cacheName) {
  return caches.open(cacheName).then((cache) =>
    cache.match(request).then((cached) => {
      const fresh = fetch(request)
        .then((response) => {
          if (cacheableResponse(response)) cache.put(request, response.clone());
          return response;
        })
        .catch(() => cached);
      return cached || fresh;
    }),
  );
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC_CACHE).then((cache) =>
      Promise.all(PRECACHE_URLS.map((url) => cache.add(url).catch(() => undefined))),
    ),
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  const keep = new Set([STATIC_CACHE, PAGE_CACHE, RUNTIME_CACHE]);
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith('easyvacc-') && !keep.has(key))
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);

  if (sameOrigin(url) && shouldSkipSameOrigin(url)) return;

  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request, PAGE_CACHE, '/'));
    return;
  }

  if (sameOrigin(url) && (url.pathname.startsWith('/static/') || url.pathname === '/manifest.webmanifest')) {
    event.respondWith(cacheFirst(request, STATIC_CACHE));
    return;
  }

  if (sameOrigin(url)) {
    event.respondWith(networkFirst(request, RUNTIME_CACHE));
    return;
  }

  event.respondWith(staleWhileRevalidate(request, RUNTIME_CACHE));
});
