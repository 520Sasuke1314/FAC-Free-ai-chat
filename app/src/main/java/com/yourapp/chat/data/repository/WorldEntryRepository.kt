package com.yourapp.chat.data.repository

import com.yourapp.chat.data.local.dao.WorldBookDao
import com.yourapp.chat.data.local.dao.WorldEntryDao
import com.yourapp.chat.data.local.entity.WorldBookEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import kotlinx.coroutines.flow.Flow

class WorldEntryRepository(
    private val worldEntryDao: WorldEntryDao,
    private val worldBookDao: WorldBookDao
) {

    fun getByCardId(cardId: Long): Flow<List<WorldEntryEntity>> = worldEntryDao.getByCardId(cardId)

    /** 世界书集合列表 */
    fun getBooks(): Flow<List<WorldBookEntity>> = worldBookDao.getAll()

    fun getEntriesByBook(bookId: Long): Flow<List<WorldEntryEntity>> = worldEntryDao.getByBookId(bookId)

    /** 手动添加的全局条目 */
    fun getManualGlobal(): Flow<List<WorldEntryEntity>> = worldEntryDao.getManualGlobal()

    suspend fun getEnabledByCardId(cardId: Long): List<WorldEntryEntity> =
        worldEntryDao.getEnabledByCardId(cardId)

    suspend fun getEnabledGlobal(): List<WorldEntryEntity> =
        worldEntryDao.getEnabledGlobal()

    suspend fun insert(entry: WorldEntryEntity): Long = worldEntryDao.insert(entry)

    suspend fun update(entry: WorldEntryEntity) = worldEntryDao.update(entry)

    suspend fun delete(entry: WorldEntryEntity) = worldEntryDao.delete(entry)

    /** 新建世界书集合（导入文件时用），返回集合 id */
    suspend fun createBook(name: String): Long = worldBookDao.insert(WorldBookEntity(name = name))

    /** 删除集合及其下所有条目 */
    suspend fun deleteBook(book: WorldBookEntity) {
        worldEntryDao.deleteByBookId(book.id)
        worldBookDao.delete(book)
    }

    /** 重命名集合 */
    suspend fun renameBook(book: WorldBookEntity, newName: String) {
        worldBookDao.update(book.copy(name = newName))
    }
}
