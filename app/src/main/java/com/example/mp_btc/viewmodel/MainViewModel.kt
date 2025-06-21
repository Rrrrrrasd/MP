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
import com.example.mp_btc.network.ExchangeRateManager

// 현재 가격 정보 UI 상태를 담는 데이터 클래스
data class PriceUiState(
    val priceText: CharSequence, // 원화, 달러를 함께 표시하는 텍스트
    val changeText: String, // 가격 변동 정보 텍스트
    val changeTextColorRes: Int // 가격 변동 텍스트의 색상 리소스 ID
)

// 가격 예측 결과 UI 상태를 담는 데이터 클래스
data class PredictionUiState(
    val predictedUsdPrice: Double, // 예측된 가격 (USD)
    val lastCloseUsdPrice: Double, // 직전 종가 (USD)
    val basisTimestamp: Long, // 예측 기준일 타임스탬프
    val usdToKrwRate: Double? // 적용된 원/달러 환율
)

// 차트 업데이트에 필요한 데이터를 담는 데이터 클래스
data class ChartUpdateData(
    val klines: List<List<Any>>, // K-line(캔들) 데이터
    val sma5: List<Double>,      // 5일 단순 이동 평균
    val sma20: List<Double>,     // 20일 단순 이동 평균
    val sma60: List<Double>      // 60일 단순 이동 평균
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 데이터 소스와의 통신을 담당하는 레포지토리 및 예측 모델
    private val repository = BtcRepository()
    private val predictor = PricePredictor(application.applicationContext)

    // UI 상태를 관찰하기 위한 LiveData
    private val _priceUiState = MutableLiveData<PriceUiState>()
    val priceUiState: LiveData<PriceUiState> = _priceUiState

    private val _chartData = MutableLiveData<ChartUpdateData>()
    val chartData: LiveData<ChartUpdateData> = _chartData

    private val _predictionUiState = MutableLiveData<PredictionUiState?>()
    val predictionUiState: LiveData<PredictionUiState?> = _predictionUiState

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _usdToKrwRate = MutableLiveData<Double?>(ExchangeRateManager.usdToKrwRate)
    val usdToKrwRate: LiveData<Double?> = _usdToKrwRate

    // ViewModel 초기화 시 초기 데이터 로드
    init {
        fetchInitialData()
    }

    // 새로고침 시 필요한 초기 데이터를 로드한다.
    fun fetchInitialData() {
        viewModelScope.launch {
            _toastMessage.value = "데이터를 새로고침합니다..."
            fetchCurrentPrice()
            // ExchangeRateManager에 저장된 최신 환율 정보로 LiveData 업데이트
            if (_usdToKrwRate.value != ExchangeRateManager.usdToKrwRate) {
                _usdToKrwRate.value = ExchangeRateManager.usdToKrwRate
            }
        }
    }

    // API에서 현재 비트코인 가격 정보를 가져와 UI 상태를 업데이트한다.
    private fun fetchCurrentPrice() {
        viewModelScope.launch {
            repository.get24hrTicker()
                .onSuccess { data ->
                    // ExchangeRateManager의 환율 정보를 사용해 UI 상태 포맷팅
                    _priceUiState.value = formatPriceUiState(data, ExchangeRateManager.usdToKrwRate)
                }
                .onFailure { _toastMessage.value = "가격 정보 로드 실패: ${it.message}" }
        }
    }

    // 선택된 기간에 맞는 과거 시세 데이터를 API에서 가져오고, 이동평균선을 계산하여 차트 데이터를 업데이트한다.
    fun fetchHistoricalData(daysPeriod: String) {
        viewModelScope.launch {
            // 기간에 따라 API 요청 파라미터 설정
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
                        // 기술적 지표 계산
                        val sma5 = com.example.mp_btc.domain.TechnicalIndicatorCalculator.calculateSMA(closePrices, 5)
                        val sma20 = com.example.mp_btc.domain.TechnicalIndicatorCalculator.calculateSMA(closePrices, 20)
                        val sma60 = com.example.mp_btc.domain.TechnicalIndicatorCalculator.calculateSMA(closePrices, 60)

                        _chartData.value = ChartUpdateData(klines, sma5, sma20, sma60)
                    }
                }
                .onFailure { _toastMessage.value = "과거 시세 로드 실패: ${it.message}" }
        }
    }

    // TFLite 모델을 사용해 오늘의 종가를 예측하고 UI 상태를 업데이트한다.
    fun predictPrice() {
        viewModelScope.launch {
            _toastMessage.value = "예측 값을 계산 중입니다..."
            _predictionUiState.value = null // 이전 예측 상태 초기화

            // 예측에 필요한 최근 100일치 데이터 요청
            repository.getHistoricalData("1d", null, 100)
                .onSuccess { klines ->
                    if (klines.isNotEmpty()) {
                        val predictedUsd = predictor.predict(klines)
                        val lastKline = klines.last()
                        val lastCloseUsd = (lastKline[4] as String).toDouble()
                        val basisTimestamp = (lastKline[0] as Double).toLong()

                        if (predictedUsd != null) {
                            // 예측 성공 시 UI 상태 업데이트
                            _predictionUiState.value = PredictionUiState(
                                predictedUsdPrice = predictedUsd,
                                lastCloseUsdPrice = lastCloseUsd,
                                basisTimestamp = basisTimestamp,
                                usdToKrwRate = ExchangeRateManager.usdToKrwRate
                            )
                        } else {
                            _toastMessage.value = "예측 값을 계산하는데 실패했습니다."
                        }
                    } else {
                        _toastMessage.value = "예측에 필요한 데이터가 부족합니다."
                    }
                }
                .onFailure { _toastMessage.value = "예측용 데이터 로드 실패: ${it.message}" }
        }
    }

    // API 응답 데이터를 화면에 표시할 PriceUiState 객체로 변환한다.
    private fun formatPriceUiState(data: com.example.mp_btc.model.Binance24hrTickerResponse, rate: Double?): PriceUiState {
        val context = getApplication<Application>().applicationContext
        val currentPriceUsd = data.lastPrice.toDouble()
        val priceChangePercent = data.priceChangePercent.toDouble()

        val priceText: CharSequence
        val changeText: String

        if (rate != null) { // 환율 정보가 있을 경우
            val currentPriceKrw = currentPriceUsd * rate
            val absoluteChangeKrw = data.priceChange.toDouble() * rate

            // SpannableString을 사용해 원화와 달러 가격의 스타일을 다르게 설정
            val krwStr = DecimalFormat("'₩',##0").format(currentPriceKrw)
            val usdStrInParentheses = " (${DecimalFormat("'$',##0.00").format(currentPriceUsd)})"
            priceText = SpannableString(krwStr + usdStrInParentheses).apply {
                setSpan(RelativeSizeSpan(0.7f), krwStr.length, this.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_secondary_dark)), krwStr.length, this.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val formattedChangeKrw = DecimalFormat("'₩',##0").format(absoluteChangeKrw)
            val formattedPercentage = DecimalFormat("+#0.00'%';-#0.00'%'").format(priceChangePercent)
            changeText = String.format(Locale.getDefault(), "%s (%s)", formattedChangeKrw, formattedPercentage)
        } else { // 환율 정보가 없을 경우
            priceText = DecimalFormat("'$',##0.00").format(currentPriceUsd)
            val formattedAbsoluteChange = DecimalFormat("+#,##0.00;-#,##0.00").format(data.priceChange.toDouble())
            val formattedPercentage = DecimalFormat("+#0.00'%';-#0.00'%'").format(priceChangePercent)
            changeText = String.format(Locale.getDefault(), "%s (%s)", formattedAbsoluteChange, formattedPercentage)
        }

        val changeColor = if (priceChangePercent >= 0) R.color.positive_green else R.color.negative_red

        return PriceUiState(priceText, changeText, changeColor)
    }
}