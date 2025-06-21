package com.example.mp_btc

import android.app.Application
import com.example.mp_btc.network.ExchangeRateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 앱 프로세스가 시작될 때 초기화 작업을 수행하는 커스텀 Application 클래스.
 */
class MyApplication : Application() {

    // 앱의 생명주기와 함께하는 코루틴 스코프.
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    /**
     * 앱이 처음 생성될 때 호출된다.
     */
    override fun onCreate() {
        super.onCreate()
        // 앱 시작과 동시에 환율 정보를 비동기적으로 가져오는 작업을 시작한다.
        // 이 작업은 UI를 막지 않고 백그라운드에서 수행된다.
        applicationScope.launch {
            ExchangeRateManager.initializeRate()
        }
    }
}