
const CACHE_NAME = 'plantcare-gemini-v1';
const urlsToCache = [
  './GEMINI-plant_identifier_app.html',
  './manifest.json'
];

// Evento de instalación: se abre el caché y se añaden los archivos principales
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('Cache abierto');
        return cache.addAll(urlsToCache);
      })
  );
});

// Evento fetch: intercepta las peticiones y responde desde el caché si es posible
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        // Si el recurso está en caché, lo devuelve
        if (response) {
          return response;
        }
        // Si no, realiza la petición a la red
        return fetch(event.request);
      })
  );
});
