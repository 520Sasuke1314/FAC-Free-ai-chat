package com.yourapp.chat.data.repository

import com.yourapp.chat.data.local.dao.ApiProfileDao
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import kotlinx.coroutines.flow.Flow

class ApiProfileRepository(private val dao: ApiProfileDao) {

    fun getAll(): Flow<List<ApiProfileEntity>> = dao.getAll()

    suspend fun getAllOnce(): List<ApiProfileEntity> = dao.getAllOnce()

    suspend fun getById(id: Long): ApiProfileEntity? = dao.getById(id)

    suspend fun getDefault(): ApiProfileEntity? = dao.getDefault()

    suspend fun save(profile: ApiProfileEntity, makeDefault: Boolean): Long {
        val id = if (profile.id == 0L) {
            dao.insert(profile)
        } else {
            dao.update(profile)
            profile.id
        }
        if (makeDefault) setDefault(id)
        return id
    }

    suspend fun setDefault(id: Long) {
        dao.clearDefault()
        dao.getById(id)?.let { dao.update(it.copy(isDefault = true)) }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
