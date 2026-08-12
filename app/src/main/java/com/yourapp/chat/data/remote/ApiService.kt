package com.yourapp.chat.data.remote

import com.yourapp.chat.data.remote.model.ChatRequest
import com.yourapp.chat.data.remote.model.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {
    @POST
    suspend fun chatCompletion(@Url url: String, @Body request: ChatRequest): Response<ChatResponse>
}
