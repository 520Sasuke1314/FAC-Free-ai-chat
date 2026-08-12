package com.yourapp.chat

import android.app.Application
import android.content.Context
import com.yourapp.chat.data.local.AppDatabase
import com.yourapp.chat.data.remote.ApiService
import com.yourapp.chat.data.remote.DeepSeekWebClient
import com.yourapp.chat.data.remote.AnthropicClient
import com.yourapp.chat.data.remote.SseClient
import com.yourapp.chat.data.repository.ApiProfileRepository
import com.yourapp.chat.data.repository.CharacterCardRepository
import com.yourapp.chat.data.repository.ChatRepository
import com.yourapp.chat.data.repository.ConfigRepository
import com.yourapp.chat.data.repository.DeepSeekWebRepository
import com.yourapp.chat.data.repository.SkillRepository
import com.yourapp.chat.data.repository.SavedModelRepository
import com.yourapp.chat.data.repository.WorldEntryRepository
import com.yourapp.chat.util.CrashLog
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ChatApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // 流式响应不限时
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val apiService: ApiService by lazy {
        // 动态 baseUrl 通过仓库传入，这里用占位
        Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
    val sseClient: SseClient by lazy { SseClient(okHttpClient) }
    val anthropicClient: AnthropicClient by lazy { AnthropicClient(okHttpClient) }
    val configRepository: ConfigRepository by lazy {
        ConfigRepository.create(this, database.apiConfigDao())
    }
    val apiProfileRepository: ApiProfileRepository by lazy { ApiProfileRepository(database.apiProfileDao()) }
    val savedModelRepository: SavedModelRepository by lazy { SavedModelRepository(database.savedModelDao()) }
    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            database,
            apiService,
            sseClient,
            anthropicClient,
            configRepository,
            worldEntryRepository,
            apiProfileRepository,
            deepSeekWebRepository
        )
    }
    val characterCardRepository: CharacterCardRepository by lazy {
        CharacterCardRepository(this, database.characterCardDao(), database)
    }
    val worldEntryRepository: WorldEntryRepository by lazy {
        WorldEntryRepository(database.worldEntryDao(), database.worldBookDao())
    }
    val deepSeekWebClient: DeepSeekWebClient by lazy { DeepSeekWebClient(okHttpClient) }
    val deepSeekWebRepository: DeepSeekWebRepository by lazy {
        DeepSeekWebRepository(this, deepSeekWebClient)
    }
    val skillRepository: SkillRepository by lazy {
        SkillRepository(database.skillDao(), okHttpClient)
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        // 全局崩溃日志捕获：闪退时把堆栈写入文件，便于定位。
        // 必须保存并调用【旧的】默认处理器，否则会无限递归导致进程卡死（黑屏）。
        // 注意只记录真正的闪退：正常退出/协程取消/网络收尾抛出的 CancellationException、
        // InterruptedException、IOException 属正常抖动（如后台断流、进程被系统回收），
        // 误记会让「崩溃日志」在没闪退时也有内容。
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val benign = throwable is java.util.concurrent.CancellationException ||
                throwable is InterruptedException ||
                throwable is java.io.IOException
            if (!benign) CrashLog.log(this, throwable)
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ChatApplication? = null

        /** 在 Application.onCreate 之后可安全调用；若未初始化则从 context 反查 */
        fun get(context: Context): ChatApplication {
            INSTANCE?.let { return it }
            synchronized(this) {
                INSTANCE?.let { return it }
                val app = context.applicationContext as ChatApplication
                INSTANCE = app
                return app
            }
        }

        val instance: ChatApplication
            get() = INSTANCE
                ?: throw IllegalStateException("ChatApplication 未初始化（应在 Application.onCreate 后访问）")
    }
}
