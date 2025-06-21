package com.example.mp_btc

import android.app.Application
import com.example.mp_btc.network.ExchangeRateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 앱 시작 시 초기화 작업을 수행하는 커스텀 Application 클래스.
 */
class MyApplication : Application() {

    // 앱의 생명주기와 함께하는 코루틴 스코프
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 앱이 생성될 때 환율 정보를 비동기적으로 가져오는 작업을 시작합니다.
        applicationScope.launch {
            ExchangeRateManager.initializeRate()
        }
    }
}