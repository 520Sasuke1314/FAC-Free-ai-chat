package com.yourapp.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourapp.chat.data.local.converter.Converters
import com.yourapp.chat.data.local.dao.ApiConfigDao
import com.yourapp.chat.data.local.dao.ApiProfileDao
import com.yourapp.chat.data.local.dao.CharacterCardDao
import com.yourapp.chat.data.local.dao.ConversationDao
import com.yourapp.chat.data.local.dao.MessageDao
import com.yourapp.chat.data.local.dao.SkillDao
import com.yourapp.chat.data.local.dao.WorldBookDao
import com.yourapp.chat.data.local.dao.WorldEntryDao
import com.yourapp.chat.data.local.dao.SavedModelDao
import com.yourapp.chat.data.local.entity.ApiConfigEntity
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import com.yourapp.chat.data.local.entity.ConversationEntity
import com.yourapp.chat.data.local.entity.MessageEntity
import com.yourapp.chat.data.local.entity.SkillEntity
import com.yourapp.chat.data.local.entity.WorldBookEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import com.yourapp.chat.data.local.entity.SavedModelEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        CharacterCardEntity::class,
        ApiConfigEntity::class,
        WorldEntryEntity::class,
        WorldBookEntity::class,
        ApiProfileEntity::class,
        SkillEntity::class,
        SavedModelEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun characterCardDao(): CharacterCardDao
    abstract fun apiConfigDao(): ApiConfigDao
    abstract fun apiProfileDao(): ApiProfileDao
    abstract fun worldEntryDao(): WorldEntryDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun skillDao(): SkillDao
    abstract fun savedModelDao(): SavedModelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `world_entries` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`cardId` INTEGER, " +
                            "`keys` TEXT NOT NULL, " +
                            "`content` TEXT NOT NULL, " +
                            "`enabled` INTEGER NOT NULL, " +
                            "`priority` INTEGER NOT NULL, " +
                            "`comment` TEXT, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_entries_cardId` ON `world_entries` (`cardId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN useCardWorld INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN useGlobalWorld INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 世界书集合表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `world_books` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                // world_entries 增加 bookId 列（旧数据全部视为手动条目，bookId = NULL）
                db.execSQL("ALTER TABLE world_entries ADD COLUMN bookId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_entries_bookId` ON `world_entries` (`bookId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `api_profiles` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`provider` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`baseUrl` TEXT NOT NULL, " +
                            "`apiKey` TEXT NOT NULL, " +
                            "`model` TEXT NOT NULL, " +
                            "`iconDomain` TEXT, " +
                            "`isDefault` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                // 旧单条配置迁移为首个 profile
                db.execSQL(
                    "INSERT INTO api_profiles (provider, name, baseUrl, apiKey, model, iconDomain, isDefault, createdAt) " +
                            "SELECT 'custom', '旧配置', baseUrl, apiKey, model, NULL, 1, 0 FROM api_config WHERE id = 0"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN showThinking INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN maxOutputTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN maxContextMessages INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN temperature REAL NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN topK INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN topP REAL NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN userGreeting TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN aiGreeting TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skills` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`description` TEXT NOT NULL, " +
                            "`content` TEXT NOT NULL, " +
                            "`source` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                // ConversationEntity 新增 selectedWorldBookIdsJson 列
                db.execSQL("ALTER TABLE conversations ADD COLUMN selectedWorldBookIdsJson TEXT NOT NULL DEFAULT '[]'")
                // ConversationEntity 新增 useCharacterCard 列（默认 false = 0）
                db.execSQL("ALTER TABLE conversations ADD COLUMN useCharacterCard INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ConversationEntity 新增 lastUsedProfileId 列（每个对话记住上次使用的 API）
                db.execSQL("ALTER TABLE conversations ADD COLUMN lastUsedProfileId INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // MessageEntity 新增 isFavorite 列（消息收藏）
                db.execSQL("ALTER TABLE messages ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ConversationEntity 新增 pinned 列（对话置顶，右滑切换）
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ApiProfileEntity 新增 protocol 列（openai / anthropic）
                db.execSQL("ALTER TABLE api_profiles ADD COLUMN protocol TEXT NOT NULL DEFAULT 'openai'")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // MessageEntity 新增 pinned 列（收藏列表置顶）
                db.execSQL("ALTER TABLE messages ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ConversationEntity 新增 thinkingLevel 列（思考力度滑块：-1 自动 / 0 不思考 / 1-5）
                db.execSQL("ALTER TABLE conversations ADD COLUMN thinkingLevel INTEGER NOT NULL DEFAULT -1")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ConversationEntity 新增 compressionModel 列（上下文压缩专用模型名，空 = 复用聊天模型）
                db.execSQL("ALTER TABLE conversations ADD COLUMN compressionModel TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新增 saved_models 表：保存的模型（绑定 API 配置，声明文本/识图能力）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_models` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`apiProfileId` INTEGER NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`canText` INTEGER NOT NULL, " +
                        "`canVision` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_models_apiProfileId` ON `saved_models` (`apiProfileId`)"
                )
                // ConversationEntity 新增 visionModel 列（识图专用模型名）
                db.execSQL("ALTER TABLE conversations ADD COLUMN visionModel TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
