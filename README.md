![Deploy Status](https://github.com/ClaudioAbril/PlantCare/actions/workflows/pages/pages-build-deployment/badge.svg)

# PlantCare

PWA de análisis de plantas (motor Pl@ntNet) + **app Android nativa** (POC) que analiza
fotos de plantas con la IA de [NVIDIA NIM](https://build.nvidia.com)
(modelo `google/diffusiongemma-26b-a4b-it`): identifica la especie, evalúa su salud,
diagnostica enfermedades y sugiere remedios caseros en español.

## App Android (POC)

Descargá el APK desde el teléfono e instalalo (habilitá "instalar apps desconocidas"
para el navegador):

📲 **[PlantCare-android.apk](android/dist/PlantCare-android.apk)**

- Toma una foto o elegí una de la galería → "🌿 Analizar" → la respuesta llega en ~10-30 s.
- ⚙️ **Configuración**: desde la app se elige el modelo y se pega la API key de
  [build.nvidia.com](https://build.nvidia.com). Un desplegable lista los modelos habilitados
  para esa clave (traídos de la API NIM). Se guarda en el teléfono y se puede cambiar cuando
  haga falta, sin recompilar.
- La clave en `android/local.properties` (no commiteada) pasa a ser **opcional**: funciona
  como fallback inicial si todavía no se configuró ninguna en la app.
  **solo para este POC personal, no distribuir el APK**.

### Reconstruir el APK

```bat
cd android
:: necesitás local.properties con sdk.dir (NIM_API_KEY es opcional, es solo fallback)
gradlew assembleDebug
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.
