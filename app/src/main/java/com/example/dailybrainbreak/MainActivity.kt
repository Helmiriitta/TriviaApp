package com.example.dailybrainbreak

import android.annotation.SuppressLint
import androidx.compose.material.icons.filled.ArrowBack
import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import android.os.Bundle
import android.text.Html
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                TriviaApp()
            }
        }
    }
}

interface TriviaApiService {
    @GET("api.php")
    suspend fun getDailyChallenge(
        @Query("amount") amount: Int = 5,
        @Query("difficulty") difficulty: String = "easy",
        @Query("type") type: String = "multiple"
    ): TriviaApiResponse
}

data class TriviaApiResponse(
    val results: List<TriviaQuestion>
)

data class TriviaQuestion(
    val question: String,
    @SerializedName("correct_answer") val correctAnswer: String,
    @SerializedName("incorrect_answers") val incorrectAnswers: List<String>,
    val category: String
)

object RetrofitInstance {
    private const val BASE_URL = "https://opentdb.com/"

    val apiService: TriviaApiService by lazy {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(TriviaApiService::class.java)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriviaApp() {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Trivia of the Day",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
            )
        },
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { HomeScreen(navController, innerPadding) }
                composable("history") { HistoryScreen(navController) }
                composable("historyDetails/{id}") { backStackEntry ->
                    val gameId = backStackEntry.arguments?.getString("id")
                    HistoryDetailsScreen(gameId = gameId, navController = navController)
                }
            }



        }
    }

}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar(
        modifier = Modifier.height(50.dp)
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = navController.currentDestination?.route == "home",
            onClick = { navController.navigate("home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "History") },
            label = { Text("History") },
            selected = navController.currentDestination?.route == "history",
            onClick = { navController.navigate("history") }
        )
    }
}


fun TriviaQuestion.decodeHtml(): TriviaQuestion {
    return this.copy(
        question = Html.fromHtml(this.question, Html.FROM_HTML_MODE_LEGACY).toString(),
        correctAnswer = Html.fromHtml(this.correctAnswer, Html.FROM_HTML_MODE_LEGACY).toString(),
        incorrectAnswers = this.incorrectAnswers.map {
            Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
        }
    )
}

@Composable
fun HomeScreen(navController: NavHostController, innerPadding: PaddingValues) {
    val context = LocalContext.current
    var triviaQuestions by remember { mutableStateOf<List<TriviaQuestion>>(emptyList()) }
    var currentQuestionIndex by rememberSaveable { mutableStateOf(0) }
    var score by rememberSaveable { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGameOver by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val answersList = remember { mutableStateOf(emptyList<String>()) }


    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.apiService.getDailyChallenge(5)
                withContext(Dispatchers.Main) {
                    triviaQuestions = response.results.map { it.decodeHtml() }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Error fetching trivia: ${e.message}"
                    isLoading = false
                }
            }
        }
    }


    if (isGameOver && triviaQuestions.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (!isGameOver && triviaQuestions.isNotEmpty()) {
                val currentQuestion = triviaQuestions[currentQuestionIndex]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Category: ${currentQuestion.category}", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentQuestion.question, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val allAnswers = (currentQuestion.incorrectAnswers + currentQuestion.correctAnswer).shuffled()

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(allAnswers.take(4)) { answer ->
                        Button(
                            onClick = {
                                val isCorrect = answer == currentQuestion.correctAnswer
                                if (isCorrect) score += 10

                                answersList.value = answersList.value + "$answer (Correct: $isCorrect)"

                                saveUserAnswer(context, currentQuestion.question, answer, isCorrect, score)

                                if (currentQuestionIndex < triviaQuestions.size - 1) {
                                    currentQuestionIndex++
                                } else {
                                    isGameOver = true
                                    saveGameHistory(
                                        context.getSharedPreferences("DailyBrainBreakPrefs", Context.MODE_PRIVATE),
                                        score,
                                        answersList.value,
                                        triviaQuestions
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(answer, color = Color.White)
                        }

                    }
                }
            } else if (isGameOver) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Game Over! Your Score: $score", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = {
                    isGameOver = false
                    currentQuestionIndex = 0
                    score = 0
                    triviaQuestions = emptyList()
                    answersList.value = emptyList()
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val response = RetrofitInstance.apiService.getDailyChallenge(5)
                            triviaQuestions = response.results.map { it.decodeHtml() }
                            isLoading = false
                        } catch (e: Exception) {
                            errorMessage = "Error restarting game: ${e.message}"
                        }
                    }
                }) {
                    Text("Restart Game")
                }

            }
        }
    }
}


fun saveUserAnswer(context: Context, question: String, answer: String, isCorrect: Boolean, score: Int) {
    val sharedPreferences = context.getSharedPreferences("DailyBrainBreakPrefs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()

    val answerData = "$question:$answer:$isCorrect:$score"
    val previousAnswers = sharedPreferences.getString("userAnswers", "") ?: ""
    editor.putString("userAnswers", previousAnswers + "\n" + answerData)
    editor.apply()
}

@Composable
fun HistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("DailyBrainBreakPrefs", Context.MODE_PRIVATE)
    val gameHistory = remember { mutableStateOf<List<GameHistory>>(emptyList()) }

    LaunchedEffect(Unit) {
        val historyJson = sharedPreferences.getString("gameHistory", "[]") ?: "[]"
        gameHistory.value = try {
            val historyList = Gson().fromJson(historyJson, Array<GameHistory>::class.java).toList()
            historyList.sortedByDescending { it.date }
        } catch (e: Exception) {
            Log.e("HistoryScreen", "Error parsing game history", e)
            emptyList()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Game History", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (gameHistory.value.isEmpty()) {
            Text("No game history available.", color = MaterialTheme.colorScheme.error)
        } else {
            val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm", Locale.getDefault())

            LazyColumn {
                items(gameHistory.value) { game ->
                    val formattedDate = dateFormat.format(game.date)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$formattedDate - Score: ${game.score}", modifier = Modifier.weight(1f))
                        Button(onClick = {
                            navController.navigate("historyDetails/${game.date.time}")
                        }) {
                            Text("Details")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryDetailsScreen(gameId: String?, navController: NavHostController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("DailyBrainBreakPrefs", Context.MODE_PRIVATE)
    val gameHistory = try {
        Gson().fromJson(sharedPreferences.getString("gameHistory", "[]") ?: "[]", Array<GameHistory>::class.java).toList()
    } catch (e: Exception) {
        Log.e("HistoryDetailsScreen", "Error parsing game history", e)
        emptyList<GameHistory>()
    }

    val game = gameHistory.find { it.date.time.toString() == gameId }

    if (game == null) {
        Text("Game not found.")
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    IconButton(onClick = {
                        val shareText = buildString {
                            append("Game Details:\n")
                            append("Date: ${game.date}\n")
                            append("Score: ${game.score}\n")
                            game.questions?.forEachIndexed { index, question ->
                                append("Question ${index + 1}: $question\n")
                                append("Answer: ${game.answers.getOrNull(index)}\n")
                                append("Correct Answer: ${game.correctAnswers.getOrNull(index)}\n")
                            }
                        }

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }

                        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Game Details", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Date: ${game.date}")
                Text("Score: ${game.score}")
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(game.questions?.size ?: 0) { index ->
                val question = game.questions?.get(index) ?: "No question"
                val answer = game.answers.getOrNull(index) ?: "No answer"
                val correctAnswer = game.correctAnswers.getOrNull(index) ?: "Unknown"

                Text("${index + 1}/5 Question:")
                Text("    Question: $question")
                Text("    Your answer: $answer")
                Text("    Correct answer: $correctAnswer")
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

data class GameHistory(
    val date: Date,
    val score: Int,
    val questions: List<String>,
    val answers: List<String>,
    val correctAnswers: List<String>
)



fun saveGameHistory(sharedPreferences: SharedPreferences, score: Int, answers: List<String>, triviaQuestions: List<TriviaQuestion>) {
    val correctAnswers = triviaQuestions.map { it.correctAnswer }
    val gameHistory = GameHistory(Date(), score, triviaQuestions.map { it.question }, answers, correctAnswers)
    val currentHistory = sharedPreferences.getString("gameHistory", "[]") ?: "[]"

    val updatedHistory = try {
        Gson().fromJson(currentHistory, Array<GameHistory>::class.java).toMutableList()
    } catch (e: Exception) {
        Log.e("saveGameHistory", "Error parsing current game history", e)
        mutableListOf<GameHistory>()
    }

    updatedHistory.add(gameHistory)

    val updatedHistoryJson = Gson().toJson(updatedHistory)
    sharedPreferences.edit().putString("gameHistory", updatedHistoryJson).apply()
}

