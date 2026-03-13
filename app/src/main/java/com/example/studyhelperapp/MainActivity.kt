package com.example.studyhelperapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.studyhelperapp.ui.theme.StudyHelperAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyHelperAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StudyHelperScreen()
                }
            }
        }
    }
}

@Composable
fun StudyHelperScreen() {
    val scope = rememberCoroutineScope()
    var userInput by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("Your answer will appear here...") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("Q&A") }

    val apiKey = BuildConfig.GEMINI_API_KEY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "📚 Study Helper",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(
                onClick = { selectedMode = "Q&A" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMode == "Q&A")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Q&A") }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { selectedMode = "Summarize" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMode == "Summarize")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Summarize") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = {
                Text(
                    if (selectedMode == "Q&A")
                        "Ask a study question..."
                    else
                        "Paste text to summarize..."
                )
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (userInput.isNotBlank()) {
                    isLoading = true
                    aiResponse = ""
                    scope.launch {
                        try {
                            val prompt = if (selectedMode == "Q&A") {
                                "You are a friendly academic tutor for students aged 16-22. Explain this in simple, clear language using easy examples. Keep your answer under 150 words. End with one helpful tip.\n\nQuestion: $userInput"
                            } else {
                                "Summarize the following text into 3-5 bullet points. Each bullet must be one clear sentence. Highlight only the most important concepts.\n\nText: $userInput"
                            }
                            aiResponse = callGeminiAPI(prompt, apiKey)
                        } catch (e: Exception) {
                            aiResponse = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Thinking..." else "Ask AI ✨")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = aiResponse,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

suspend fun callGeminiAPI(prompt: String, apiKey: String): String {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"

        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "${prompt.replace("\"", "\\\"").replace("\n", "\\n")}" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        val json = JSONObject(responseBody)

// Show full response if candidates missing
        if (!json.has("candidates")) {
            return@withContext "API Error: $responseBody"
        }

        json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
}