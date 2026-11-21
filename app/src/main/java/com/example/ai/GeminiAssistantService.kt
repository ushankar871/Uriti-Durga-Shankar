package com.example.ai

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.SmartFillResult
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

private const val TAG = "GeminiAssistant"

class GeminiAssistantService {

    private val chatModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Lakshmi Sahayak, a knowledgeable Indian grocery, fancy items, and Hindu puja samagri seller assistant. " +
                        "Help the seller manage present stock vs. out-of-stock visibility, suggest festival puja checklists " +
                        "(e.g. Ganesh Puja, Diwali, Navratri, Satyanarayan Puja), craft clear product descriptions, " +
                        "and recommend smart restock quantities. Keep replies warm, practical, concise, and structured."
                )
            }
        )
    }

    private val smartFillSchema = Schema.obj(
        properties = mapOf(
            "category" to Schema.enumeration(
                values = listOf("GROCERY", "PUJA", "FANCY"),
                description = "Category of the item: GROCERY, PUJA, or FANCY"
            ),
            "unit" to Schema.string(description = "Typical retail unit e.g. 500 g, 1 kg, 1 pack, 2 pcs"),
            "priceInr" to Schema.double(description = "Suggested typical Indian retail price in INR"),
            "description" to Schema.string(description = "Concise 1-sentence buyer-friendly description under 140 chars"),
            "imageTag" to Schema.enumeration(
                values = listOf(
                    "rice", "dal", "ghee", "produce", "spices",
                    "diya", "camphor", "incense", "flowers", "kumkum",
                    "fancy", "bangles", "personal_care", "general"
                ),
                description = "Closest visual artwork tag"
            )
        )
    )

    private val smartFillModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = smartFillSchema
            }
        )
    }

    @OptIn(PublicPreviewAPI::class)
    private val ttsModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.1-flash-tts-preview",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(
                    voice = Voice("Kore"),
                    languageCode = "en-US"
                )
            }
        )
    }

    private var activeChat: Chat? = null
    private var activeAudioTrack: AudioTrack? = null
    @OptIn(PublicPreviewAPI::class)
    private var activeLiveSession: LiveSession? = null

    fun resetChat() {
        activeChat = null
    }

    fun sendChatStream(userPrompt: String, inventoryContext: String): Flow<String> = flow {
        val chat = activeChat ?: chatModel.startChat().also { activeChat = it }
        val enrichedPrompt = if (inventoryContext.isNotBlank()) {
            "Current store stock snapshot:\n$inventoryContext\n\nSeller question: $userPrompt"
        } else {
            userPrompt
        }
        val sb = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(enrichedPrompt).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    sb.append(piece)
                    emit(sb.toString())
                }
            }
        }
        if (sb.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response.")
        }
    }

    suspend fun smartFillProduct(itemName: String): SmartFillResult {
        val response = withTimeout(45_000L) {
            smartFillModel.generateContent(
                "Generate retail catalog metadata for an Indian grocery, fancy items, or puja samagri item named: \"$itemName\"."
            )
        }
        val jsonText = response.text ?: throw IllegalStateException("Empty smart-fill response.")
        val json = JSONObject(jsonText)
        return SmartFillResult(
            category = ProductCategory.fromRaw(json.optString("category")),
            unit = json.optString("unit", "1 pack").ifBlank { "1 pack" },
            priceInr = json.optDouble("priceInr", 99.0).coerceAtLeast(1.0),
            description = json.optString("description", "").take(250),
            imageTag = json.optString("imageTag", "general")
        )
    }

    suspend fun generateRestockInsights(products: List<Product>): String {
        val inStockList = products.filter { it.inStock }.joinToString { "${it.name} (${it.stockQty} ${it.unit})" }
        val outOfStockList = products.filter { !it.inStock }.joinToString { "${it.name} (${it.categoryEnum().displayName})" }
        val prompt = """
            Analyze this Indian Grocery, Fancy Items & Puja Samagri seller's current stock visibility:
            Present Stock: ${inStockList.ifBlank { "None" }}
            Out of Stock: ${outOfStockList.ifBlank { "None" }}

            Provide 3 concise, high-impact recommendations:
            1) Top Out-of-Stock items to restock urgently and why.
            2) A ready-to-promote Puja + Fancy + Grocery Combo Bundle using currently Present Stock items.
            3) One quick customer visibility tip.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            chatModel.generateContent(prompt)
        }
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No insights returned by Gemini.")
    }

    suspend fun speakTextWithGeminiTts(textToRead: String) {
        stopTts()
        val safeText = textToRead.trim().take(600)
        require(safeText.isNotBlank()) { "Nothing to speak." }

        val response = withTimeout(60_000L) {
            ttsModel.generateContent("Read clearly in a warm, helpful shopkeeper tone: $safeText")
        }
        val part = response.candidates.firstOrNull()?.content?.parts
            ?.filterIsInstance<InlineDataPart>()
            ?.firstOrNull()
            ?: throw IllegalStateException("TTS response did not contain audio data.")

        val pcmBytes = part.inlineData
        playPcmAudio(pcmBytes)
    }

    private suspend fun playPcmAudio(pcmBytes: ByteArray) = withContext(Dispatchers.IO) {
        if (pcmBytes.isEmpty()) return@withContext
        val sampleRate = 24_000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmBytes.size)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, pcmBytes.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeAudioTrack = track
        track.write(pcmBytes, 0, pcmBytes.size)
        track.play()
        // Wait roughly for playback duration before releasing
        val durationMs = (pcmBytes.size.toLong() * 1000L) / (sampleRate * 2L)
        kotlinx.coroutines.delay(durationMs.coerceAtLeast(300L) + 150L)
        try {
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack release warning: ${e.message}")
        } finally {
            if (activeAudioTrack === track) activeAudioTrack = null
        }
    }

    fun stopTts() {
        try {
            activeAudioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        } finally {
            activeAudioTrack = null
        }
    }

    @OptIn(PublicPreviewAPI::class)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startLiveVoiceSession(
        getStockSnapshot: () -> List<Product>,
        onToggleItemStock: (itemName: String, inStock: Boolean) -> String
    ) {
        stopLiveVoiceSession()

        val getStockDeclaration = FunctionDeclaration(
            name = "get_stock_summary",
            description = "Get the current list of Present Stock (in-stock) and Out-of-Stock grocery and puja items.",
            parameters = emptyMap()
        )
        val setStockDeclaration = FunctionDeclaration(
            name = "set_item_stock",
            description = "Update an item's stock visibility status to in-stock or out-of-stock by item name.",
            parameters = mapOf(
                "itemName" to Schema.string("The grocery or puja item name"),
                "inStock" to Schema.boolean("True to mark Present Stock, false to mark Out of Stock")
            )
        )

        val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = "gemini-3.1-flash-live-preview",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("Kore"))
            },
            systemInstruction = content {
                text(
                    "You are Lakshmi Live Voice Assistant for a puja needs, fancy items, and grocery store. " +
                        "Use get_stock_summary to answer questions about present stock or out-of-stock items, " +
                        "and use set_item_stock when the seller asks to mark an item in stock or out of stock. " +
                        "Keep spoken replies brief and natural."
                )
            },
            tools = listOf(Tool.functionDeclarations(listOf(getStockDeclaration, setStockDeclaration)))
        )

        val session = withTimeout(30_000L) { liveModel.connect() }
        activeLiveSession = session
        session.startAudioConversation { functionCall: FunctionCallPart ->
            when (functionCall.name) {
                "get_stock_summary" -> {
                    val items = getStockSnapshot()
                    val present = items.filter { it.inStock }.joinToString { "${it.name} (${it.stockQty} ${it.unit})" }
                    val outOfStock = items.filter { !it.inStock }.joinToString { it.name }
                    FunctionResponsePart(
                        functionCall.name,
                        JsonObject(
                            mapOf(
                                "presentStock" to JsonPrimitive(present.ifBlank { "None" }),
                                "outOfStock" to JsonPrimitive(outOfStock.ifBlank { "None" })
                            )
                        )
                    )
                }

                "set_item_stock" -> {
                    val itemName = functionCall.args["itemName"]?.jsonPrimitive?.content.orEmpty()
                    val inStock = functionCall.args["inStock"]?.jsonPrimitive?.booleanOrNull ?: true
                    val statusMsg = onToggleItemStock(itemName, inStock)
                    FunctionResponsePart(
                        functionCall.name,
                        JsonObject(
                            mapOf(
                                "success" to JsonPrimitive(true),
                                "message" to JsonPrimitive(statusMsg)
                            )
                        )
                    )
                }

                else -> FunctionResponsePart(
                    functionCall.name,
                    JsonObject(mapOf("error" to JsonPrimitive("Unknown function: ${functionCall.name}")))
                )
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun stopLiveVoiceSession() {
        try {
            activeLiveSession?.stopAudioConversation()
            activeLiveSession?.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiveSession: ${e.message}")
        } finally {
            activeLiveSession = null
        }
    }
}
