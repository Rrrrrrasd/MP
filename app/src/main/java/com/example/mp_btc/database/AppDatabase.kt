package com.example.mp_btc.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.mp_btc.model.Transaction

/**
 * 앱의 Room 데이터베이스를 정의하고 초기화한다.
 * Transaction 엔티티를 관리한다.
 */
@Database(entities = [Transaction::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Transaction 데이터에 접근하기 위한 DAO(Data Access Object)를 반환한다.
     */
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 데이터베이스의 싱글턴 인스턴스를 가져온다.
         * 인스턴스가 없으면 새로 생성하여 반환한다.
         *
         * @param context 애플리케이션 컨텍스트
         * @return AppDatabase 인스턴스
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "btc_portfolio_database" // 데이터베이스 파일명
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}