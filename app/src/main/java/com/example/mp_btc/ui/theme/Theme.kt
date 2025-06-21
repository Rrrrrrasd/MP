package com.example.mp_btc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 다크 모드에서 사용될 색상 팔레트 정의
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// 라이트 모드에서 사용될 색상 팔레트 정의
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * 앱의 메인 Compose 테마.
 * 시스템 설정에 따라 라이트/다크 테마 및 동적 색상을 적용한다.
 * @param darkTheme 다크 테마를 강제로 사용할지 여부. 기본값은 시스템 설정을 따른다.
 * @param dynamicColor 동적 색상(Material You)을 사용할지 여부. Android 12 이상에서만 가능.
 * @param content 테마를 적용할 Composable UI 내용.
 */
@Composable
fun Mp_btcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 동적 색상은 Android 12(S) 이상에서만 지원된다.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 적용할 색상 스킴(ColorScheme) 결정
    val colorScheme = when {
        // 동적 색상이 가능하고 활성화된 경우
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 동적 색상을 사용하지 않거나 불가능한 경우, 미리 정의된 색상 사용
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 최종적으로 결정된 색상 및 타이포그래피를 MaterialTheme에 적용
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}