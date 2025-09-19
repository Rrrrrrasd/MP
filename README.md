# BitCoin Tracker


#### 220124126 유연보
### 1. 사용기술스택
- 언어: Kotlin
- 네트워킹: Retrofit2 & Gson
- 데이터베이스: Room Persistence Library 
- UI: XML, ViewBinding, MPAndroidChart, Material Components, Glide
- 머신러닝: TensorFlow Lite
- 비동기 처리: Kotlin Coroutines & viewModelScope
- 아키텍처 컴포넌트: ViewModel, LiveData
- 아키텍처: MVVM(Model-View-ViewModel)
  ```
  a. View: Activity와 XML 레이아웃을 ViewBinding을 사용하여 XML 뷰와 코드간의 상호작용을 처리
  Ex) MainActivity, PortfolioActivity 등
  b. ViewModel: UI에 필요한 데이터를 LiveData로 노출하고 비즈니스 로직과 데이터를 처리
  Ex) MainViewModel, PortfolioViewModel, NewsViewModel 등
  c. Model: 데이터 소스(API, DB)에 대한 접근을 추상화 하여 ViewModel이 데이터 소스의 구체적인 구현을 알 필요가 없도록함
  Ex) BtcRepository, PortfolioRepository 등
  ```

  
### 2. 주요기능
- 실시간 비트코인 시세 및 차트 제공
바이낸스 API를 통해 비트코인의 실시간 가격과 변동률을 확인하고 기간별(1일~전체) 과거 시세 데이터를 라인 차트로 시각화하여 제공
- 머신러닝 모델을 이용한 비트코인 종가 예측
TensorFlow Lite 머신러닝 모델을 활용해 과거 데이터를 기반으로 다음 날의 비트코인 종가를 예측
- 포트폴리오 관리
사용자가 자신의 비트코인 매수/매도 내역을 기록하고 현재 가치, 총 보유량, 수익률 등 개인화된 포트폴리오 정보를 관리할 수 있다.
- 비트코인 관련 최신 뉴스 제공
NewsAPI를 통해 비트코인과 관련된 최신 뉴스를 받아와 사용자에게 제공한다.

### 3. 프로젝트 구조
- Database 폴더: Room 데이터베이스 설정 및 DAO(Data Access Object)
- Domain 폴더: 핵심 비즈니스 로직 (가격 예측, 기술 지표 계산 등)
- Model 폴더: API 응답 및 데이터 베이스 테이블을 위한 데이터 클래스
- Network 폴더: Retrofit를 이용한 네트워크 통신 관련 클래스(API클라이언트, 서비스 인터페이스)
- Repository 폴더: 데이터 소스를 추상화 하는 저장소 클래스
- Ui 폴더: View와 관련된 클래스 (Adapter, ChartManager, MarkerView 등)
- Viewmodel 폴더: MVVM패턴의 ViewModel클래스
- Activities: 각 화면을 담당하는 Activity

### 4. 흐름도
<img width="1015" height="697" alt="image" src="https://github.com/user-attachments/assets/cf963492-fa40-470a-b0b2-4fc21714e79c" />

### 5. 실행화면
<img width="500" height="800" alt="image" src="https://github.com/user-attachments/assets/4badbf3a-5d76-433f-9fe2-3f3690e509f7" />
<img width="500" height="800" alt="image" src="https://github.com/user-attachments/assets/21a60879-7580-457d-bc03-5567743ef96c" />
<img width="500" height="800" alt="image" src="https://github.com/user-attachments/assets/60feb9a7-3eaf-4786-ac90-61c770fa874e" />
<img width="500" height="800" alt="image" src="https://github.com/user-attachments/assets/e02b66d8-1d86-4e9c-813c-22c8e0883ac0" />
<img width="500" height="800" alt="image" src="https://github.com/user-attachments/assets/9153ee4f-eb1b-4a55-b3ef-7e1c6d86b917" />
<img width="500" height="800" alt="image" src="https://github.com/user-attachments/assets/1b554e9d-e609-4c9b-bca3-358a97600a6b" />






