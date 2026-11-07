package ar.plantcare.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Configuración del motor: NVIDIA NIM (modelo google/diffusiongemma-26b-a4b-it)
// ---------------------------------------------------------------------------

private const val NIM_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
private const val NIM_MODELS_URL = "https://integrate.api.nvidia.com/v1/models"
private const val NIM_MODEL = "google/diffusiongemma-26b-a4b-it"

// JPEG 8x8 (verde) para el test de conexión: valida el mismo camino que el análisis,
// que envía imágenes. Ojo: los modelos sin visión la ignoran sin error (probado con
// gpt-oss-20b, responde 200 con content null).
private const val TEST_IMAGE_B64 =
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsND" +
        "hIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQU" +
        "FBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAAIAAgDASIAAhEBAxEB/8Q" +
        "AHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBR" +
        "IhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTV" +
        "FVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLD" +
        "xMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAEC" +
        "AwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSM" +
        "zUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eH" +
        "l6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5e" +
        "bn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5yooor8PP5vP/2Q=="

private const val SYSTEM_PROMPT = """
Eres un experto botánico y fitopatólogo. Analiza la imagen de la planta que recibe el usuario.
Responde ÚNICAMENTE con un objeto JSON válido (sin markdown, sin texto alrededor) en español, con esta estructura exacta:
{
  "name": "nombre común de la especie",
  "scientific": "nombre científico",
  "family": "familia botánica",
  "flowers_or_fruits": "época de floración/fructificación y características",
  "health": "estado general de la planta (p.ej. Saludable, o el nombre del problema principal)",
  "severity": "ninguna|leve|moderada|grave",
  "confidence": 85,
  "identification": "descripción del análisis: qué planta es, sus especificaciones (floración, cuidados, etc.) y si está enferma, qué enfermedad tiene y cómo curarla",
  "problems": [{"problem": "nombre de la enfermedad/plaga", "symptoms": "síntomas visibles", "cause": "causa probable"}],
  "homeRemedies": [{"title": "título del remedio", "treats": "para qué es", "ingredients": ["..."], "preparation": "cómo prepararlo", "application": "cómo aplicarlo", "frequency": "frecuencia", "warning": "precauciones"}],
  "wellnessTips": [{"title": "título", "text": "texto"}],
  "recommendations": [{"title": "título", "text": "texto"}],
  "alternatives": [{"name": "otra especie posible", "confidence": 60}]
}
Si la planta está sana, "problems" y "homeRemedies" deben ser arrays vacíos. confidence es un número del 0 al 100.
"""

private const val USER_TEXT = "Dime que planta es esta, sus especificaciones como floración, cuidados, etc. Si ves que esta enferma, dime que enfermedad tiene, como se puede curar, y si puedo ayudarla con algún remedio casero."

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------

enum class Phase { IDLE, LOADING, READY, ANALYZING, ERROR, RESULT }

private fun severityColor(severity: String?): Color = when (severity?.lowercase()?.trim()) {
    "ninguna" -> Color(0xFF2E7D32)
    "leve" -> Color(0xFF827717)
    "moderada" -> Color(0xFFEF6C00)
    "grave" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}

private fun cleanModelJson(raw: String): String {
    var s = raw.trim()
    if (s.startsWith("```")) {
        s = s.removePrefix("```json").removePrefix("```").trim()
        val end = s.lastIndexOf("```")
        if (end >= 0) s = s.substring(0, end).trim()
    }
    return s
}

private fun JSONObject.str(key: String): String = optString(key, "").trim().ifBlank { "—" }

/** Traduce un error de red de OkHttp a una pista concreta en español. */
private fun describeNetError(e: Exception): String = when (e) {
    is java.net.UnknownHostException ->
        "el teléfono no pudo resolver integrate.api.nvidia.com. Revisá que tenga " +
            "internet (WiFi o datos) y, si el WiFi pide login en el navegador (portal cautivo), " +
            "conectate primero."
    is java.net.SocketTimeoutException ->
        "se agotó el tiempo de espera: la red del teléfono no llega al servidor. " +
            "Probá cambiar de WiFi a datos móviles (o viceversa)."
    is java.net.ConnectException ->
        "la conexión fue rechazada por la red (¿VPN, firewall o proxy?)."
    is javax.net.ssl.SSLException ->
        "error de cifrado TLS: ${e.message}"
    else -> e.message ?: e.javaClass.simpleName
}

// ---------------------------------------------------------------------------
// Actividad
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    // Estado observable desde Compose
    var uiPhase by mutableStateOf(Phase.IDLE)
        private set
    var uiBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var uiResult by mutableStateOf<JSONObject?>(null)
        private set
    var uiError by mutableStateOf("")
        private set

    // Estado de la ventana de configuración
    var uiSettingsOpen by mutableStateOf(false)
        private set
    var uiApiKeyDraft by mutableStateOf("")
        private set
    var uiModelDraft by mutableStateOf("")
        private set
    var uiModels by mutableStateOf<List<String>>(emptyList())
        private set
    var uiModelsLoading by mutableStateOf(false)
        private set
    var uiModelsMsg by mutableStateOf("")
        private set
    var uiTestRunning by mutableStateOf(false)
        private set
    var uiTestMsg by mutableStateOf("")
        private set

    // Fotos de referencia de la especie detectada (iNaturalist / Wikimedia Commons)
    var uiRefPhotos by mutableStateOf<List<Bitmap>>(emptyList())
        private set
    var uiRefPhotoUrls by mutableStateOf<List<String>>(emptyList())
        private set
    var uiRefPhotosLoading by mutableStateOf(false)
        private set
    var uiRefPhotosMsg by mutableStateOf("")
        private set

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Cliente para el sondeo de vigencia: muchas consultas cortas en paralelo. */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 16
        })
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraFile: File? = null

    private val pickGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadFromUri(uri)
        }

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraFile?.let { loadFromUri(Uri.fromFile(it)) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("PlantCare · Análisis con IA") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                ) { padding ->
                    when (uiPhase) {
                        Phase.LOADING -> LoadingView("Cargando imagen…", padding)
                        Phase.ANALYZING ->
                            LoadingView(
                                "Analizando la planta con la IA…\n(puede tardar 10–30 s)",
                                padding
                            )
                        Phase.ERROR -> ErrorView(uiError, this, padding)
                        Phase.IDLE -> IdleView(this, padding)
                        Phase.READY -> ReadyView(this, padding)
                        Phase.RESULT -> ResultScreen(this, padding)
                    }
                }
                if (uiSettingsOpen) {
                    SettingsSheet()
                }
            }
        }
    }

    /** Prepara la foto de cámara: archivo temporal + URI compartido vía FileProvider. */
    fun prepareCameraCapture(): Uri {
        val dir = File(filesDir, "cam").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        cameraFile = file
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    fun launchGallery() = pickGallery.launch("image/*")

    fun launchCamera() = takePhoto.launch(prepareCameraCapture())

    // ------------------------------------------------------------------
    // Configuración: API key y modelo (persisten en el teléfono)
    // ------------------------------------------------------------------

    private val prefs by lazy {
        getSharedPreferences("plantcare_settings", Context.MODE_PRIVATE)
    }

    private fun savedApiKey(): String = prefs.getString("nim_api_key", "")?.trim().orEmpty()

    /** Clave efectiva: la guardada en Configuración o, si no hay, la embebida en el APK. */
    fun effectiveApiKey(): String = savedApiKey().ifBlank { BuildConfig.NIM_API_KEY.trim() }

    /** Modelo efectivo: el guardado en Configuración o, si no hay, el por defecto. */
    fun effectiveModel(): String =
        prefs.getString("nim_model", "")?.trim().orEmpty().ifBlank { NIM_MODEL }

    fun openSettings() {
        uiApiKeyDraft = savedApiKey()
        uiModelDraft = effectiveModel()
        uiModelsMsg = ""
        uiTestMsg = ""
        uiSettingsOpen = true
        // Con una clave efectiva ya disponible (guardada o embebida), el listado se trae solo.
        if (effectiveApiKey().isNotBlank() && uiModels.isEmpty()) fetchModels(uiApiKeyDraft)
    }

    fun saveSettings() {
        prefs.edit()
            .putString("nim_api_key", uiApiKeyDraft.trim())
            .putString("nim_model", uiModelDraft.trim())
            .apply()
        uiSettingsOpen = false
    }

    /** Pega el texto del portapapeles en el campo de API key (gesto del usuario). */
    fun pasteApiKey() {
        val cm = getSystemService(ClipboardManager::class.java)
        val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            uiModelsMsg = "El portapapeles está vacío."
        } else {
            uiApiKeyDraft = text
            uiModelsMsg = ""
        }
    }

    /** Trae los modelos habilitados para esta clave: GET /v1/models (OpenAI-compatible). */
    fun fetchModels(apiKey: String) {
        // Igual que en el análisis: si el campo está vacío se usa la clave embebida del APK.
        val key = apiKey.trim().ifBlank { BuildConfig.NIM_API_KEY.trim() }
        if (key.isBlank()) {
            uiModelsMsg = "Primero escribí tu API key de NIM."
            return
        }
        uiModelsLoading = true
        uiModelsMsg = ""
        val request = Request.Builder()
            .url(NIM_MODELS_URL)
            .addHeader("Authorization", "Bearer $key")
            .get()
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    uiModelsLoading = false
                    uiModelsMsg = "No se pudo conectar: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                mainHandler.post {
                    if (!response.isSuccessful) {
                        uiModelsLoading = false
                        uiModelsMsg = if (response.code == 401 || response.code == 403) {
                            "API key inválida o vencida (HTTP ${response.code})."
                        } else {
                            "Error HTTP ${response.code} al listar modelos.\n${body.take(300)}"
                        }
                    } else {
                        try {
                            val arr = JSONObject(body).getJSONArray("data")
                            val ids = mutableListOf<String>()
                            for (i in 0 until arr.length()) {
                                ids.add(arr.getJSONObject(i).optString("id"))
                            }
                            val catalog = ids.filter { it.isNotBlank() }.distinct()
                            if (catalog.isEmpty()) {
                                uiModelsLoading = false
                                uiModelsMsg = "El catálogo vino vacío (inusual). ${body.take(200)}"
                            } else {
                                // El catálogo lista también modelos vencidos y modelos sin
                                // chat: se verifica cada uno antes de mostrar la lista.
                                uiModelsMsg = "Verificando vigencia de ${catalog.size} modelos…"
                                probeModelLiveness(catalog) { eol, noChat ->
                                    uiModels = catalog.filter { it !in eol && it !in noChat }
                                        .sorted()
                                    uiModelsMsg = buildString {
                                        append("${uiModels.size} modelos de chat disponibles para tu clave.")
                                        if (eol.isNotEmpty()) {
                                            append(" Ocultados ${resumenOcultados(eol)} vencido(s) (fin de vida): ${resumenNombres(eol)}.")
                                        }
                                        if (noChat.isNotEmpty()) {
                                            append(" Ocultados ${resumenOcultados(noChat)} sin soporte de chat (embeddings u otros endpoints).")
                                        }
                                    }
                                    if (uiModels.isNotEmpty() && uiModels.none { it == uiModelDraft }) {
                                        uiModelDraft = if (uiModels.contains(NIM_MODEL)) NIM_MODEL else uiModels.first()
                                    }
                                    uiModelsLoading = false
                                }
                            }
                        } catch (e: Exception) {
                            uiModelsLoading = false
                            uiModelsMsg = "Respuesta inesperada del servidor.\n${body.take(300)}"
                        }
                    }
                }
            }
        })
    }

    /** "1 modelo" o "N modelos", para armar el mensaje de ocultados. */
    private fun resumenOcultados(set: Set<String>): String =
        if (set.size == 1) "1 modelo" else "${set.size} modelos"

    /** Hasta 3 nombres; si hay más, los completa con "y N más". */
    private fun resumenNombres(set: Set<String>): String {
        val sorted = set.sorted()
        return if (sorted.size <= 3) {
            sorted.joinToString()
        } else {
            sorted.take(3).joinToString() + " y ${sorted.size - 3} más"
        }
    }

    /**
     * Verifica la vigencia de cada modelo del catálogo contra chat/completions SIN clave:
     * el chequeo de ciclo de vida corre antes que la autenticación, así que un modelo
     * vencido responde 410 (end of life), uno sin ruta de chat responde 404 (p. ej.
     * embeddings) y uno vigente llega hasta la puerta de auth con 401. No consume cuota:
     * el pedido nunca llega a ejecutar el modelo. Ante 404 se reintenta una vez porque se
     * observó algún 404 transitorio del gateway; errores de red (-1) conservan el modelo.
     */
    private fun probeModelLiveness(
        ids: List<String>,
        onDone: (eol: Set<String>, noChat: Set<String>) -> Unit
    ) {
        val codes = ConcurrentHashMap<String, Int>()
        val pending = AtomicInteger(ids.size)
        for (id in ids) {
            probeModelOnce(id, retriesLeft = 1) { code ->
                codes[id] = code
                if (pending.decrementAndGet() == 0) {
                    val eol = codes.filterValues { it == 410 }.keys.toSet()
                    val noChat = codes.filterValues { it == 404 }.keys.toSet()
                    mainHandler.post { onDone(eol, noChat) }
                }
            }
        }
    }

    /** POST mínimo {"model": id} sin clave; reporta el código HTTP (o -1 si falló la red). */
    private fun probeModelOnce(id: String, retriesLeft: Int, onCode: (Int) -> Unit) {
        val body = "{\"model\":${JSONObject.quote(id)}}"
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(NIM_URL)
            .post(body)
            .build()
        probeClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onCode(-1)
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                response.close()
                if (code == 404 && retriesLeft > 0) {
                    probeModelOnce(id, retriesLeft = 0, onCode)
                } else {
                    onCode(code)
                }
            }
        })
    }

    /**
     * Prueba la conexión contra chat/completions con la clave y el modelo que se ven en
     * pantalla (los borradores, no los guardados). Es la validación real: /v1/models
     * responde 200 incluso con una clave corrupta, pero chat/completions no.
     *
     * El pedido incluye una imagen diminuta, igual que el análisis real. Si el modelo
     * elegido falla por disponibilidad (404/410: el catálogo de /v1/models lista modelos
     * retirados o no habilitados por cuenta), se prueba también el modelo recomendado
     * de la app para sugerir un reemplazo que funcione.
     */
    fun testConnection() {
        val key = uiApiKeyDraft.trim().ifBlank { BuildConfig.NIM_API_KEY.trim() }
        if (key.isBlank()) {
            uiTestMsg = "Primero escribí tu API key de NIM."
            return
        }
        val model = uiModelDraft.trim().ifBlank { NIM_MODEL }
        uiTestRunning = true
        uiTestMsg = ""
        val started = System.currentTimeMillis()
        postTestRequest(key, model, started) { ok, code, body, ms ->
            if (ok) {
                uiTestRunning = false
                uiTestMsg = "✅ Conexión OK: clave y modelo \"$model\" válidos (${ms / 1000.0} s). " +
                    "Ya podés Guardar y analizar plantas."
                return@postTestRequest
            }
            val base = if (code == -1) {
                "❌ Sin conexión: $body"
            } else {
                when (code) {
                    401, 403 ->
                        "❌ API key inválida o vencida (HTTP $code). " +
                            "Generá una nueva gratis en build.nvidia.com."
                    404 ->
                        "❌ El modelo \"$model\" no está disponible para tu cuenta (HTTP 404). " +
                            "El catálogo lista modelos que pueden estar retirados o no habilitados " +
                            "para tu clave.\n${body.take(200)}"
                    410 ->
                        "❌ El modelo \"$model\" fue retirado del servicio (HTTP 410, end of " +
                            "life). La lista del catálogo incluye modelos viejos.\n${body.take(200)}"
                    429 ->
                        "❌ Límite de solicitudes alcanzado (HTTP 429). Esperá unos minutos y " +
                            "volvé a probar."
                    else -> "❌ Error HTTP $code.\n${body.take(300)}"
                }
            }
            // Ante un modelo muerto, proponemos el recomendado probándolo en vivo.
            if ((code == 404 || code == 410) && model != NIM_MODEL) {
                postTestRequest(key, NIM_MODEL, System.currentTimeMillis()) { ok2, code2, body2, ms2 ->
                    uiTestRunning = false
                    uiTestMsg = base + if (ok2) {
                        "\n\n💡 Probé el modelo recomendado \"$NIM_MODEL\": ✅ funciona " +
                            "(${ms2 / 1000.0} s). Seleccionalo en la lista y Guardá."
                    } else {
                        "\n\n💡 El modelo recomendado \"$NIM_MODEL\" también falla ahora " +
                            "(HTTP $code2). ${body2.take(120)}"
                    }
                }
            } else {
                uiTestRunning = false
                uiTestMsg = base
            }
        }
    }

    /** POST mínimo a chat/completions con imagen de prueba; reporta (ok, código, cuerpo, ms). */
    private fun postTestRequest(
        key: String,
        model: String,
        startedAt: Long,
        onResult: (ok: Boolean, code: Int, body: String, ms: Long) -> Unit
    ) {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "Responde solo: OK"))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$TEST_IMAGE_B64"))
            )
        val payload = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("max_tokens", 16)
            .put("temperature", 0)
            .put("stream", false)
        if (model.contains("gemma", ignoreCase = true)) {
            payload.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
        }
        val request = Request.Builder()
            .url(NIM_URL)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        // Límite total estricto para el test: si la red está rota, no colgar la pantalla.
        val testClient = httpClient.newBuilder().callTimeout(60, TimeUnit.SECONDS).build()
        testClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onResult(false, -1, describeNetError(e), System.currentTimeMillis() - startedAt)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - startedAt
                mainHandler.post { onResult(response.isSuccessful, response.code, body, ms) }
            }
        })
    }

    // ------------------------------------------------------------------
    // Fotos de referencia de la especie detectada (internet)
    // Los modelos de NIM no navegan la web y las URLs que pudieran
    // "responder" serían alucinaciones: la búsqueda la hace la app con el
    // nombre científico, contra APIs gratuitas y sin clave.
    // ------------------------------------------------------------------

    /** Nombres que no sirven para buscar (la especie no fue identificada). */
    private fun usableSpeciesName(s: String): Boolean {
        val v = s.trim().lowercase()
        return v.isNotBlank() && v !in setOf(
            "—", "-", "–", "n/a", "na", "desconocida", "no identificada",
            "no identificable", "especie desconocida", "sin datos"
        )
    }

    private fun refClient() = httpClient.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()

    /**
     * Busca hasta 3 fotos de la especie detectada y las deja listas para la pantalla
     * de resultado. Cadena de intentos: iNaturalist (científico → común) y
     * Wikimedia Commons (científico → común). Nunca bloquea la UI ni falla duro:
     * si no hay fotos, simplemente no se muestra la sección.
     */
    fun fetchReferencePhotos(result: JSONObject) {
        val sci = result.optString("scientific").trim()
        val common = result.optString("name").trim()
        val sciOk = usableSpeciesName(sci)
        val commonOk = usableSpeciesName(common)
        if (!sciOk && !commonOk) return

        uiRefPhotos = emptyList()
        uiRefPhotoUrls = emptyList()
        uiRefPhotosMsg = ""
        uiRefPhotosLoading = true

        Thread {
            val urls = mutableListOf<String>()
            try {
                if (sciOk) fetchRefUrlsFromINat(sci, urls)
                if (urls.size < 4 && commonOk) fetchRefUrlsFromINat(common, urls)
                if (urls.size < 4 && sciOk) fetchRefUrlsFromCommons(sci, urls)
                if (urls.size < 4 && commonOk) fetchRefUrlsFromCommons(common, urls)
            } catch (e: Exception) {
                // seguimos con lo que se haya recolectado
            }
            val bitmaps = mutableListOf<Bitmap>()
            val okUrls = mutableListOf<String>()
            for (u in urls) {
                if (bitmaps.size >= 3) break
                try {
                    downloadBitmapLimited(u, 500)?.let {
                        bitmaps.add(it)
                        okUrls.add(u)
                    }
                } catch (e: Exception) {
                    // foto ilegible: probamos con la siguiente
                }
            }
            mainHandler.post {
                uiRefPhotosLoading = false
                if (bitmaps.isEmpty()) {
                    uiRefPhotosMsg = "No se pudieron cargar fotos de referencia (¿sin internet?)."
                } else {
                    uiRefPhotos = bitmaps
                    uiRefPhotoUrls = okUrls
                }
            }
        }.start()
    }

    /** Observaciones de iNaturalist con foto; el campo url es square: /medium. da ~500px. */
    private fun fetchRefUrlsFromINat(query: String, out: MutableList<String>) {
        val url = "https://api.inaturalist.org/v1/observations?photos=true&per_page=12" +
            "&order_by=votes&taxon_name=" + URLEncoder.encode(query, "UTF-8")
        val resp = refClient().newCall(
            Request.Builder().url(url).addHeader("User-Agent", "PlantCare-POC/1.5").build()
        ).execute()
        val body = resp.use { r -> if (r.isSuccessful) r.body?.string().orEmpty() else "" }
        if (body.isBlank()) return
        val results = JSONObject(body).optJSONArray("results") ?: return
        for (i in 0 until results.length()) {
            val photos = results.getJSONObject(i).optJSONArray("photos") ?: continue
            for (j in 0 until photos.length()) {
                val p = photos.getJSONObject(j)
                val u = p.optString("url").replace("/square.", "/medium.").ifBlank {
                    p.optString("medium_url")
                }
                if (u.isNotBlank() && u !in out) out.add(u)
            }
            if (out.size >= 4) return
        }
    }

    /** Búsqueda de archivos en Wikimedia Commons (miniaturas de 500px). */
    private fun fetchRefUrlsFromCommons(query: String, out: MutableList<String>) {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
            "&generator=search&gsrnamespace=6&gsrlimit=10&prop=imageinfo&iiprop=url" +
            "&iiurlwidth=500&gsrsearch=" + URLEncoder.encode(query, "UTF-8")
        val resp = refClient().newCall(
            Request.Builder().url(url)
                .addHeader("User-Agent", "PlantCare-POC/1.5 (app personal de plantas)").build()
        ).execute()
        val body = resp.use { r -> if (r.isSuccessful) r.body?.string().orEmpty() else "" }
        if (body.isBlank()) return
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return
        val keys = pages.keys()
        while (keys.hasNext() && out.size < 8) {
            val info = pages.getJSONObject(keys.next()).optJSONArray("imageinfo")?.optJSONObject(0)
                ?: continue
            val u = info.optString("thumburl")
            val lower = u.lowercase()
            if (u.isNotBlank() && u !in out &&
                (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))
            ) {
                out.add(u)
            }
        }
    }

    private fun downloadBitmapLimited(url: String, maxDim: Int): Bitmap? {
        val bytes = refClient().newCall(
            Request.Builder().url(url).addHeader("User-Agent", "PlantCare-POC/1.5").build()
        ).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / sample > maxDim || h / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun openPhotoInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // sin navegador instalado: no pasa nada
        }
    }

    fun reset() {
        uiBitmap = null
        uiResult = null
        uiError = ""
        uiPhase = Phase.IDLE
        uiRefPhotos = emptyList()
        uiRefPhotoUrls = emptyList()
        uiRefPhotosLoading = false
        uiRefPhotosMsg = ""
        cameraFile?.delete()
        cameraFile = null
    }

    // ------------------------------------------------------------------
    // Carga de imagen (hilo de fondo)
    // ------------------------------------------------------------------

    fun loadFromUri(uri: Uri) {
        mainHandler.post { uiPhase = Phase.LOADING }
        Thread {
            val bmp = try {
                decodeBitmapLimited(uri, 1280)
            } catch (e: Exception) {
                null
            }
            mainHandler.post {
                if (bmp == null) {
                    uiPhase = Phase.ERROR
                    uiError = "No se pudo leer la imagen."
                } else {
                    uiBitmap = bmp
                    uiPhase = Phase.READY
                }
            }
        }.start()
    }

    private fun decodeBitmapLimited(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / sample > maxDim || h / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    // ------------------------------------------------------------------
    // Análisis con NVIDIA NIM (hilo de fondo)
    // ------------------------------------------------------------------

    fun analyze() {
        val bitmap = uiBitmap ?: return
        val apiKey = effectiveApiKey()
        if (apiKey.isBlank()) {
            uiPhase = Phase.ERROR
            uiError = "Falta configurar la API key.\nEntrá a ⚙️ Configuración y pegá tu clave de build.nvidia.com."
            return
        }
        val model = effectiveModel()
        uiRefPhotos = emptyList()
        uiRefPhotoUrls = emptyList()
        uiRefPhotosMsg = ""
        mainHandler.post { uiPhase = Phase.ANALYZING }
        Thread {
            try {
                val payload = buildPayload(bitmap, model)
                val request = Request.Builder()
                    .url(NIM_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        mainHandler.post {
                            uiPhase = Phase.ERROR
                            uiError = "No se pudo conectar a la API: ${describeNetError(e)}\n" +
                                "Podés verificar la conexión en ⚙️ Configuración → 🧪 Probar conexión."
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string().orEmpty()
                        mainHandler.post {
                            if (!response.isSuccessful) {
                                uiPhase = Phase.ERROR
                                uiError = "Error HTTP ${response.code} del servidor NIM.\n${body.take(400)}"
                            } else {
                                try {
                                    val json = JSONObject(body)
                                    val content = json.getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("message")
                                        .getString("content")
                                    val parsed = JSONObject(cleanModelJson(content))
                                    uiResult = parsed
                                    uiPhase = Phase.RESULT
                                    fetchReferencePhotos(parsed)
                                } catch (e: Exception) {
                                    uiPhase = Phase.ERROR
                                    uiError = "La respuesta del modelo no era un JSON válido.\n${body.take(400)}"
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                mainHandler.post {
                    uiPhase = Phase.ERROR
                    uiError = "Error al preparar la petición: ${e.message}"
                }
            }
        }.start()
    }

    private fun buildPayload(bitmap: Bitmap, model: String): String {
        val w = bitmap.width
        val h = bitmap.height
        val scale = minOf(1f, 1024f / maxOf(w, h))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        val userContent = JSONArray()
            .put(JSONObject().put("type", "text").put("text", USER_TEXT))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
            )

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", userContent))

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", 4096)
            .put("temperature", 1)
            .put("top_p", 0.95)
            .put("stream", false)
        // El modo thinking solo existe en los modelos gemma; en el resto genera un 400.
        if (model.contains("gemma", ignoreCase = true)) {
            payload.put("chat_template_kwargs", JSONObject().put("enable_thinking", true))
        }
        return payload.toString()
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    @Composable
    private fun LoadingView(message: String, padding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text(message, textAlign = TextAlign.Center)
        }
    }

    @Composable
    private fun IdleView(activity: MainActivity, padding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🌿", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Toma una foto de tu planta o elegí una de la galería.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "La IA te dirá qué planta es, cómo cuidarla y, si está enferma, cómo curarla con remedios caseros.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { activity.launchCamera() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("📷 Tomar foto") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { activity.launchGallery() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🖼️ Elegir de la galería") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { activity.openSettings() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("⚙️ Configuración") }
        }
    }

    @Composable
    private fun ErrorView(message: String, activity: MainActivity, padding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚠️", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { activity.reset() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reintentar") }
        }
    }

    @Composable
    private fun SettingsSheet() {
        var showKey by remember { mutableStateOf(false) }
        var menuExpanded by remember { mutableStateOf(false) }
        var modelFilter by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { uiSettingsOpen = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "⚙️ Configuración",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = uiApiKeyDraft,
                    onValueChange = { uiApiKeyDraft = it },
                    label = { Text("API key de NVIDIA NIM") },
                    placeholder = { Text("nvapi-…") },
                    singleLine = true,
                    visualTransformation =
                        if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { pasteApiKey() }) { Text("📋") }
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "🙈" else "👁")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Generala gratis en build.nvidia.com. Se guarda solo en este teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { fetchModels(uiApiKeyDraft) },
                    enabled = !uiModelsLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiModelsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Buscando modelos…")
                    } else {
                        Text("🔄 Cargar modelos")
                    }
                }

                if (uiModelsMsg.isNotBlank()) {
                    Text(
                        uiModelsMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = modelFilter,
                    onValueChange = { modelFilter = it },
                    label = { Text("Filtrar modelos…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiModels.isEmpty() && !uiModelsLoading) {
                    Text(
                        "Todavía no cargaste la lista: tocá \"Cargar modelos\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = uiModelDraft,
                            onValueChange = { uiModelDraft = it },
                            label = { Text("Modelo") },
                            singleLine = true,
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            val f = modelFilter.trim().lowercase()
                            val items = if (f.isBlank()) {
                                uiModels
                            } else {
                                uiModels.filter { it.lowercase().contains(f) }
                            }
                            if (items.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Sin coincidencias") },
                                    onClick = { }
                                )
                            } else {
                                items.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                m,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            uiModelDraft = m
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { testConnection() },
                    enabled = !uiTestRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Probando conexión…")
                    } else {
                        Text("🧪 Probar conexión")
                    }
                }

                if (uiTestMsg.isNotBlank()) {
                    Text(
                        uiTestMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiTestMsg.startsWith("✅")) {
                            Color(0xFF2E7D32)
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { uiSettingsOpen = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancelar") }
                    Button(
                        onClick = { saveSettings() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Guardar") }
                }
            }
        }
    }

    @Composable
    private fun ResultScreen(activity: MainActivity, padding: PaddingValues) {
        val result = activity.uiResult ?: return
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            activity.uiBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto de la planta",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
            Button(
                onClick = { activity.reset() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("🔁 Nueva foto") }
            ResultView(activity, result)
            Spacer(Modifier.height(16.dp))
        }
    }

    @Composable
    private fun ReadyView(activity: MainActivity, padding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // La imagen toma solo el espacio restante (weight) y se muestra completa
            // (Fit): con FillWidth y sin límite de alto, una foto alta empujaba los
            // botones "Analizar"/"Cambiar foto" fuera de la pantalla.
            activity.uiBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto de la planta",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { activity.analyze() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) { Text("🌿 Analizar") }
                OutlinedButton(
                    onClick = { activity.reset() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) { Text("Cambiar foto") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    /** Miniaturas de la especie traídas de internet para que el usuario verifique. */
    @Composable
    private fun ReferencePhotosSection(activity: MainActivity) {
        val loading = activity.uiRefPhotosLoading
        val photos = activity.uiRefPhotos
        if (!loading && photos.isEmpty()) {
            if (activity.uiRefPhotosMsg.isNotBlank()) {
                Section("Fotos de referencia") {
                    Text(
                        activity.uiRefPhotosMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }
        Section("Fotos de referencia de la especie") {
            Text(
                "Compará estas fotos con tu planta para confirmar que la identificación " +
                    "es correcta. Tocá una para verla en grande.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Buscando fotos en internet…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.forEachIndexed { i, bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Foto de referencia ${i + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    activity.uiRefPhotoUrls.getOrNull(i)?.let { url ->
                                        activity.openPhotoInBrowser(url)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ResultView(activity: MainActivity, data: JSONObject) {
        val name = data.optString("name").trim().ifBlank { "Especie desconocida" }
        val scientific = data.optString("scientific").trim()
        val family = data.optString("family").trim()
        val flowers = data.optString("flowers_or_fruits").trim()
        val health = data.optString("health").trim().ifBlank { "Sin datos" }
        val severity = data.optString("severity").trim().ifBlank { "—" }
        val confidence = data.optInt("confidence", 0).coerceIn(0, 100)
        val identification = data.optString("identification").trim()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (scientific.isNotBlank()) {
                        Text(
                            scientific,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (family.isNotBlank()) {
                        Text(
                            "Familia: $family",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(severityColor(severity))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                severity,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            health,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { confidence / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Confianza: $confidence%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ReferencePhotosSection(activity)

            if (flowers.isNotBlank()) Section("Floración / frutos") { Text(flowers) }
            if (identification.isNotBlank()) Section("Identificación") { Text(identification) }

            JsonListSection(data, "problems", "Problemas detectados") { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.str("problem"), fontWeight = FontWeight.Bold)
                        val symptoms = p.optString("symptoms").trim()
                        val cause = p.optString("cause").trim()
                        if (symptoms.isNotBlank()) LabeledLine("Síntomas:", symptoms)
                        if (cause.isNotBlank()) LabeledLine("Causa probable:", cause)
                    }
                }
            }

            JsonListSection(data, "homeRemedies", "Remedios caseros") { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.str("title"), fontWeight = FontWeight.Bold)
                        val treats = r.optString("treats").trim()
                        if (treats.isNotBlank()) LabeledLine("Para qué sirve:", treats)
                        val ingredients = r.optJSONArray("ingredients")
                        if (ingredients != null && ingredients.length() > 0) {
                            LabeledLine(
                                "Ingredientes:",
                                (0 until ingredients.length()).joinToString(", ") {
                                    ingredients.optString(it).trim()
                                }
                            )
                        }
                        val preparation = r.optString("preparation").trim()
                        val application = r.optString("application").trim()
                        val frequency = r.optString("frequency").trim()
                        val warning = r.optString("warning").trim()
                        if (preparation.isNotBlank()) LabeledLine("Preparación:", preparation)
                        if (application.isNotBlank()) LabeledLine("Aplicación:", application)
                        if (frequency.isNotBlank()) LabeledLine("Frecuencia:", frequency)
                        if (warning.isNotBlank()) LabeledLine("⚠️ Precauciones:", warning)
                    }
                }
            }

            JsonListSection(data, "wellnessTips", "Consejos de bienestar") { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.str("title"), fontWeight = FontWeight.Bold)
                        val text = t.optString("text").trim()
                        if (text.isNotBlank()) Text(text)
                    }
                }
            }

            JsonListSection(data, "recommendations", "Recomendaciones") { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.str("title"), fontWeight = FontWeight.Bold)
                        val text = t.optString("text").trim()
                        if (text.isNotBlank()) Text(text)
                    }
                }
            }

            JsonListSection(data, "alternatives", "Otras especies posibles") { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(a.str("name"), modifier = Modifier.weight(1f))
                    Text(
                        "${a.optInt("confidence", 0)}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
            content()
        }
    }

    @Composable
    private fun JsonListSection(
        data: JSONObject,
        key: String,
        title: String,
        item: @Composable (JSONObject) -> Unit
    ) {
        val arr = data.optJSONArray(key)
        if (arr != null && arr.length() > 0) {
            Section(title) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until arr.length()) {
                        item(arr.getJSONObject(i))
                    }
                }
            }
        }
    }

    @Composable
    private fun LabeledLine(label: String, text: String) {
        if (text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "$label $text",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
