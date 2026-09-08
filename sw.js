
const CACHE_NAME = 'plantcare-gemini-v6';
const urlsToCache = [
  './index.html',
  './GEMINI-plant_identifier_app.html',
  './manifest.json'
];

// Evento de instalación: se abre el caché y se añaden los archivos principales.
// skipWaiting(): el service worker nuevo se activa en cuanto termina de instalar,
// sin esperar a que se cierren todas las pestañas, para que las actualizaciones lleguen antes.
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('Cache abierto');
        return cache.addAll(urlsToCache);
      })
      .then(() => self.skipWaiting())
  );
});

// Evento de activación: borra cachés de versiones anteriores y toma el control
// de las páginas abiertas inmediatamente (clients.claim).
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

// Evento fetch:
// - Para navegaciones (HTML): red primero y caché de respaldo. Así la app siempre
//   muestra la última versión publicada cuando hay conexión, y funciona sin ella.
// - Para el resto de recursos: caché primero, red como respaldo.
self.addEventListener('fetch', event => {
  const request = event.request;
  const isNavigation = request.mode === 'navigate';
  const isHtml = request.method === 'GET' &&
    request.headers.get('accept') &&
    request.headers.get('accept').indexOf('text/html') !== -1;

  if (isNavigation || isHtml) {
    event.respondWith(
      fetch(request)
        .then(response => {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
          return response;
        })
        .catch(() => caches.match(request))
    );
    return;
  }

  event.respondWith(
    caches.match(request)
      .then(response => response || fetch(request))
  );
});
