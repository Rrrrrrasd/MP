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

data class PriceUiState(
    val priceText: CharSequence,
    val changeText: String,
    val changeTextColorRes: Int
)

data class PredictionUiState(
    val predictedUsdPrice: Double,
    val displayString: String
)

data class ChartUpdateData(
    val klines: List<List<Any>>,
    val sma5: List<Double>,
    val sma20: List<Double>,
    val sma60: List<Double>
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BtcRepository()
    private val predictor = PricePredictor(application.applicationContext)

    private val _usdToKrwRate = MutableLiveData<Double?>(null)
    val usdToKrwRate: LiveData<Double?> = _usdToKrwRate

    private val _priceUiState = MutableLiveData<PriceUiState>()
    val priceUiState: LiveData<PriceUiState> = _priceUiState

    private val _chartData = MutableLiveData<ChartUpdateData>()
    val chartData: LiveData<ChartUpdateData> = _chartData

    private val _predictionUiState = MutableLiveData<PredictionUiState?>()
    val predictionUiState: LiveData<PredictionUiState?> = _predictionUiState

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    /**
     * ViewModel 초기화 시, 현재 가격과 환율 정보를 가져옵니다.
     */
    init {
        fetchInitialData()
    }

    /**
     * 현재 가격과 환율 정보를 포함한 초기 데이터를 로드합니다. (새로고침 시 사용)
     */
    fun fetchInitialData() {
        viewModelScope.launch {
            _toastMessage.value = "데이터를 새로고침합니다..."
            fetchCurrentPrice()
            repository.getExchangeRate()
                .onSuccess { rates ->
                    if (rates.isNotEmpty() && rates.first().result == 1) {
                        _usdToKrwRate.value = rates.find { it.currencyUnit == "USD" }
                            ?.dealBaseRate?.replace(",", "")?.toDoubleOrNull()
                    } else {
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

    /**
     * 현재 비트코인 가격 정보를 API로부터 가져와 UI 상태를 업데이트합니다.
     */
    private fun fetchCurrentPrice() {
        viewModelScope.launch {
            repository.get24hrTicker()
                .onSuccess { data ->
                    _priceUiState.value = formatPriceUiState(data, _usdToKrwRate.value)
                }
                .onFailure { _toastMessage.value = "가격 정보 로드 실패: ${it.message}" }
        }
    }

    /**
     * 선택된 기간에 맞는 과거 시세 데이터를 API로부터 가져오고,
     * 이동평균선을 계산하여 차트 데이터를 업데이트합니다.
     * @param daysPeriod 사용자가 선택한 기간 (e.g., "1", "30", "max").
     */
    fun fetchHistoricalData(daysPeriod: String) {
        viewModelScope.launch {
            val (interval, startTime, limit) = when (daysPeriod) {
                "1" -> Triple("5m", System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L), null)
                "5" -> Triple("30m", System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L), null)
                "30" -> Triple("4h", System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L), null)
                "180" -> Triple("1d", System.currentTimeMillis() - (180 * 24 * 60 * 60 * 1000L), 240)
                "365" -> Triple("1d", System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L), 425)
                "max" -> Triple("1d", null, 1000)
                else -> Triple("1d", null, null)
            }

            repository.getHistoricalData(interval, startTime, limit)
                .onSuccess { klines ->
                    if (klines.isNotEmpty()) {
                        val closePrices = klines.map { (it[4] as String).toDouble() }
                        val sma5 = com.example.mp_btc.domain.TechnicalIndicatorCalculator.calculateSMA(closePrices, 5)
                        val sma20 = com.example.mp_btc.domain.TechnicalIndicatorCalculator.calculateSMA(closePrices, 20)
                        val sma60 = com.example.mp_btc.domain.TechnicalIndicatorCalculator.calculateSMA(closePrices, 60)

                        _chartData.value = ChartUpdateData(klines, sma5, sma20, sma60)
                    }
                }
                .onFailure { _toastMessage.value = "과거 시세 로드 실패: ${it.message}" }
        }
    }

    /**
     * TFLite 모델을 사용하여 오늘의 종가를 예측하고 UI 상태를 업데이트합니다.
     */
    fun predictPrice() {
        viewModelScope.launch {
            _toastMessage.value = "예측 값을 계산 중입니다..."
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

    /**
     * 예측된 USD 가격을 환율에 따라 원화 또는 달러 문자열로 포맷팅합니다.
     * @param predictedUsd 예측된 가격 (USD).
     * @param rate USD/KRW 환율.
     * @return 화면에 표시될 최종 문자열.
     */
    private fun formatPrediction(predictedUsd: Double, rate: Double?): String {
        return if (rate != null) {
            val krwPrice = predictedUsd * rate
            "₩" + DecimalFormat("#,##0").format(krwPrice)
        } else {
            "$" + DecimalFormat("#,##0.00").format(predictedUsd) + " (환율 정보 없음)"
        }
    }

    /**
     * API로부터 받은 Ticker 데이터를 화면에 표시할 UI 상태 객체로 변환합니다.
     * @param data 바이낸스 24시간 Ticker 응답 데이터.
     * @param rate USD/KRW 환율.
     * @return 가격 표시를 위한 PriceUiState 객체.
     */
    private fun formatPriceUiState(data: com.example.mp_btc.model.Binance24hrTickerResponse, rate: Double?): PriceUiState {
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