package com.example.mp_btc.viewmodel

import android.app.Application
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.mp_btc.R
import com.example.mp_btc.domain.PricePredictor
import com.example.mp_btc.repository.BtcRepository
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Locale

// UI 상태를 나타내는 데이터 클래스
data class PriceUiState(
    val priceText: CharSequence,
    val changeText: String,
    val changeTextColorRes: Int // 리소스 ID를 전달하여 Context 의존성 제거
)

// 예측 결과를 담을 데이터 클래스 (표시될 최종 문자열 포함)
data class PredictionUiState(
    val predictedUsdPrice: Double,
    val displayString: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BtcRepository()
    private val predictor = PricePredictor(application.applicationContext)

    private val _usdToKrwRate = MutableLiveData<Double?>(null)
    val usdToKrwRate: LiveData<Double?> = _usdToKrwRate // ChartManager에서 사용할 수 있도록 public으로 유지

    private val _priceUiState = MutableLiveData<PriceUiState>()
    val priceUiState: LiveData<PriceUiState> = _priceUiState

    private val _chartData = MutableLiveData<List<List<Any>>>()
    val chartData: LiveData<List<List<Any>>> = _chartData

    // 예측 결과를 UI State 객체로 관리
    private val _predictionUiState = MutableLiveData<PredictionUiState?>()
    val predictionUiState: LiveData<PredictionUiState?> = _predictionUiState

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    init {
        fetchInitialData()
    }

    private fun fetchInitialData() {
        viewModelScope.launch {
            fetchCurrentPrice()
            repository.getExchangeRate()
                .onSuccess { rates ->
                    if (rates.isNotEmpty() && rates.first().result == 1) { // result 코드 확인
                        _usdToKrwRate.value = rates.find { it.currencyUnit == "USD" }
                            ?.dealBaseRate?.replace(",", "")?.toDoubleOrNull()
                    } else {
                        // API가 오류 코드를 반환한 경우 (예: 인증 실패, 데이터 없음 등)
                        val reason = when (rates.firstOrNull()?.result) {
                            2 -> "DATA 코드 오류"
                            3 -> "인증키 오류"
                            4 -> "일일 요청 횟수 초과"
                            else -> "알 수 없는 API 오류"
                        }
                        _toastMessage.value = "환율 정보 API 오류: $reason"
                    }
                }
                .onFailure {
                    _toastMessage.value = "환율 정보 로드 실패 (네트워크 오류)"
                }
        }
    }

    private fun fetchCurrentPrice() {
        viewModelScope.launch {
            repository.get24hrTicker()
                .onSuccess { data ->
                    _priceUiState.value = formatPriceUiState(data, _usdToKrwRate.value)
                }
                .onFailure { _toastMessage.value = "가격 정보 로드 실패: ${it.message}" }
        }
    }

    fun fetchHistoricalData(daysPeriod: String) {
        viewModelScope.launch {
            val (interval, startTime, limit) = when (daysPeriod) {
                "1" -> Triple("5m", System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L), null)
                "5" -> Triple("30m", System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L), null)
                "30" -> Triple("4h", System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L), null)
                "180" -> Triple("1d", System.currentTimeMillis() - (180 * 24 * 60 * 60 * 1000L), null)
                "365" -> Triple("1d", System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L), null)
                "max" -> Triple("1d", null, 1000)
                else -> Triple("1d", null, null)
            }

            repository.getHistoricalData(interval, startTime, limit)
                .onSuccess { klines -> _chartData.value = klines }
                .onFailure { _toastMessage.value = "과거 시세 로드 실패: ${it.message}" }
        }
    }

    fun predictPrice() {
        viewModelScope.launch {
            _toastMessage.value = "예측 값을 계산 중입니다..."
            // 예측 후 UI state가 null이 되도록 하여, 버튼을 다시 눌렀을 때도 Activity 이동이 일어나도록 함
            _predictionUiState.value = null

            repository.getHistoricalData("1d", null, 100)
                .onSuccess { klines ->
                    val predictedUsd = predictor.predict(klines)
                    if (predictedUsd != null) {
                        val displayString = formatPrediction(predictedUsd, _usdToKrwRate.value)
                        _predictionUiState.value = PredictionUiState(predictedUsd, displayString)
                    } else {
                        _toastMessage.value = "예측 값을 계산하는데 실패했습니다."
                    }
                }
                .onFailure { _toastMessage.value = "예측용 데이터 로드 실패: ${it.message}" }
        }
    }

    private fun formatPrediction(predictedUsd: Double, rate: Double?): String {
        return if (rate != null) {
            val krwPrice = predictedUsd * rate
            "₩" + DecimalFormat("#,##0").format(krwPrice)
        } else {
            "$" + DecimalFormat("#,##0.00").format(predictedUsd) + " (환율 정보 없음)"
        }
    }

    private fun formatPriceUiState(data: com.example.mp_btc.model.Binance24hrTickerResponse, rate: Double?): PriceUiState {
        // ... 이 함수의 내용은 이전과 동일 ...
        val context = getApplication<Application>().applicationContext
        val currentPriceUsd = data.lastPrice.toDouble()
        val priceChangePercent = data.priceChangePercent.toDouble()

        val priceText: CharSequence
        val changeText: String

        if (rate != null) {
            val currentPriceKrw = currentPriceUsd * rate
            val absoluteChangeKrw = data.priceChange.toDouble() * rate

            val krwStr = DecimalFormat("'₩',##0").format(currentPriceKrw)
            val usdStrInParentheses = " (${DecimalFormat("'$',##0").format(currentPriceUsd)})"
            val spannable = SpannableString(krwStr + usdStrInParentheses)
            spannable.setSpan(RelativeSizeSpan(0.7f), krwStr.length, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_secondary_dark)), krwStr.length, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            priceText = spannable

            val formattedChangeKrw = DecimalFormat("'₩',##0").format(absoluteChangeKrw)
            val formattedPercentage = DecimalFormat("+#0.00'%';-#0.00'%'").format(priceChangePercent)
            changeText = String.format(Locale.getDefault(), "%s (%s)", formattedChangeKrw, formattedPercentage)
        } else {
            priceText = DecimalFormat("'$',##0.00").format(currentPriceUsd)

            val formattedAbsoluteChange = DecimalFormat("+#,##0.0;-#,##0.0").format(data.priceChange.toDouble())
            val formattedPercentage = DecimalFormat("+#0.00'%';-#0.00'%'").format(priceChangePercent)
            changeText = String.format(Locale.getDefault(), "%s (%s)", formattedAbsoluteChange, formattedPercentage)
        }

        val changeColor = if (priceChangePercent >= 0) R.color.positive_green else R.color.negative_red

        return PriceUiState(priceText, changeText, changeColor)
    }
}