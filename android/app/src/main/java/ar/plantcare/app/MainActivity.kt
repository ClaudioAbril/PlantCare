package ar.plantcare.app

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
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
private const val NIM_MODEL = "google/diffusiongemma-26b-a4b-it"

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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
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

    fun reset() {
        uiBitmap = null
        uiResult = null
        uiError = ""
        uiPhase = Phase.IDLE
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
        mainHandler.post { uiPhase = Phase.ANALYZING }
        Thread {
            try {
                val payload = buildPayload(bitmap)
                val request = Request.Builder()
                    .url(NIM_URL)
                    .addHeader("Authorization", "Bearer ${BuildConfig.NIM_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        mainHandler.post {
                            uiPhase = Phase.ERROR
                            uiError = "No se pudo conectar a la API: ${e.message}"
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
                                    uiResult = JSONObject(cleanModelJson(content))
                                    uiPhase = Phase.RESULT
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

    private fun buildPayload(bitmap: Bitmap): String {
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

        return JSONObject()
            .put("model", NIM_MODEL)
            .put("messages", messages)
            .put("max_tokens", 4096)
            .put("temperature", 1)
            .put("top_p", 0.95)
            .put("stream", false)
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", true))
            .toString()
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
            ResultView(result)
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
            activity.uiBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto de la planta",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
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

    @Composable
    private fun ResultView(data: JSONObject) {
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
