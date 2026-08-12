package com.yourapp.chat.data.repository

import com.yourapp.chat.data.local.dao.SavedModelDao
import com.yourapp.chat.data.local.entity.SavedModelEntity
import kotlinx.coroutines.flow.Flow

class SavedModelRepository(private val dao: SavedModelDao) {

    fun getAll(): Flow<List<SavedModelEntity>> = dao.getAll()

    suspend fun getAllOnce(): List<SavedModelEntity> = dao.getAllOnce()

    fun getByProfile(apiProfileId: Long): Flow<List<SavedModelEntity>> = dao.getByProfile(apiProfileId)

    fun getTextModels(): Flow<List<SavedModelEntity>> = dao.getTextModels()

    fun getVisionModels(): Flow<List<SavedModelEntity>> = dao.getVisionModels()

    suspend fun getById(id: Long): SavedModelEntity? = dao.getById(id)

    suspend fun save(model: SavedModelEntity): Long = dao.insert(model)

    suspend fun update(model: SavedModelEntity) = dao.update(model)

    suspend fun delete(model: SavedModelEntity) = dao.delete(model)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
