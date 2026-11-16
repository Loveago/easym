package com.easymomo.app.data.remote

import com.easymomo.app.data.remote.dto.AuthResponse
import com.easymomo.app.data.remote.dto.CustomerListResponse
import com.easymomo.app.data.remote.dto.CustomerSyncRequest
import com.easymomo.app.data.remote.dto.CustomerSyncResponse
import com.easymomo.app.data.remote.dto.CustomerSyncStatusDto
import com.easymomo.app.data.remote.dto.DataBundlesResponse
import com.easymomo.app.data.remote.dto.InitializePaymentRequest
import com.easymomo.app.data.remote.dto.InitializePaymentResponse
import com.easymomo.app.data.remote.dto.LoginRequest
import com.easymomo.app.data.remote.dto.PaymentsResponse
import com.easymomo.app.data.remote.dto.PlansResponse
import com.easymomo.app.data.remote.dto.RegisterRequest
import com.easymomo.app.data.remote.dto.SubscriptionStatusWrapper
import com.easymomo.app.data.remote.dto.SyncTransactionsRequest
import com.easymomo.app.data.remote.dto.SyncTransactionsResponse
import com.easymomo.app.data.remote.dto.UssdFlowsResponse
import com.easymomo.app.data.remote.dto.VerifyPaymentResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EasyMoMoApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("subscriptions/status")
    suspend fun getSubscriptionStatus(): Response<SubscriptionStatusWrapper>

    @GET("subscriptions/plans")
    suspend fun getSubscriptionPlans(): Response<PlansResponse>

    @POST("subscriptions/initialize")
    suspend fun initializePayment(@Body request: InitializePaymentRequest): Response<InitializePaymentResponse>

    @GET("subscriptions/verify/{reference}")
    suspend fun verifyPayment(@Path("reference") reference: String): Response<VerifyPaymentResponse>

    @GET("subscriptions/payments")
    suspend fun getPaymentHistory(): Response<PaymentsResponse>

    @POST("transactions/sync")
    suspend fun syncTransactions(@Body request: SyncTransactionsRequest): Response<SyncTransactionsResponse>

    @GET("config/ussd-flows")
    suspend fun getUssdFlows(): Response<UssdFlowsResponse>

    @GET("config/bundles")
    suspend fun getDataBundles(@Query("network") network: String? = null): Response<DataBundlesResponse>

    @GET("customers")
    suspend fun getCustomers(
        @Query("since") since: Long? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("includeDeleted") includeDeleted: Boolean = true
    ): Response<CustomerListResponse>

    @POST("customers/sync")
    suspend fun syncCustomers(@Body request: CustomerSyncRequest): Response<CustomerSyncResponse>

    @GET("customers/status")
    suspend fun getCustomerSyncStatus(): Response<CustomerSyncStatusDto>
}
