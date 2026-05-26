package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@Entity(tableName = "cached_months")
data class CachedMonthEntity(
    @PrimaryKey val yearMonth: String, // format "YYYY-MM"
    val jsonData: String
)

@Entity(tableName = "cached_years")
data class CachedYearEntity(
    @PrimaryKey val year: Int,
    val jsonData: String
)

@Dao
interface CachedDataDao {
    @Query("SELECT * FROM cached_months")
    suspend fun getAllCachedMonths(): List<CachedMonthEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMonth(cachedMonth: CachedMonthEntity)

    @Query("SELECT * FROM cached_years")
    suspend fun getAllCachedYears(): List<CachedYearEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedYear(cachedYear: CachedYearEntity)
}

@Database(entities = [CachedMonthEntity::class, CachedYearEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedDataDao(): CachedDataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "odia_calendar_cache_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object SerializationHelper {
    fun festivalToJson(f: Festival): JSONObject {
        return JSONObject().apply {
            put("date", f.date.toString())
            put("nameEn", f.nameEn)
            put("nameOr", f.nameOr)
            put("isMajor", f.isMajor)
        }
    }

    fun jsonToFestival(obj: JSONObject): Festival {
        return Festival(
            date = LocalDate.parse(obj.getString("date")),
            nameEn = obj.getString("nameEn"),
            nameOr = obj.getString("nameOr"),
            isMajor = obj.optBoolean("isMajor", false)
        )
    }

    fun odiaDayInfoToJson(info: OdiaDayInfo): JSONObject {
        return JSONObject().apply {
            put("date", info.date.toString())
            put("odiaMonthEn", info.odiaMonth.nameEn)
            put("odiaMonthOr", info.odiaMonth.nameOr)
            put("tithiNameEn", info.tithi.nameEn)
            put("tithiNameOr", info.tithi.nameOr)
            put("tithiValue", info.tithi.value)
            put("pakshaEn", info.paksha.nameEn)
            put("pakshaOr", info.paksha.nameOr)
            put("rashiEn", info.rashi.nameEn)
            put("rashiOr", info.rashi.nameOr)
            put("rashiSymbol", info.rashi.symbol)
            
            val festArray = JSONArray()
            info.festivals.forEach { fest ->
                festArray.put(festivalToJson(fest))
            }
            put("festivals", festArray)
        }
    }

    fun jsonToOdiaDayInfo(obj: JSONObject): OdiaDayInfo {
        val date = LocalDate.parse(obj.getString("date"))
        val odiaMonth = OdiaMonthInfo(
            nameEn = obj.getString("odiaMonthEn"),
            nameOr = obj.getString("odiaMonthOr")
        )
        val tithi = TithiInfo(
            nameEn = obj.getString("tithiNameEn"),
            nameOr = obj.getString("tithiNameOr"),
            value = obj.getInt("tithiValue")
        )
        val paksha = PakshaInfo(
            nameEn = obj.getString("pakshaEn"),
            nameOr = obj.getString("pakshaOr")
        )
        val rashi = RashiInfo(
            nameEn = obj.getString("rashiEn"),
            nameOr = obj.getString("rashiOr"),
            symbol = obj.getString("rashiSymbol")
        )
        val festivalsArray = obj.getJSONArray("festivals")
        val festivalsList = mutableListOf<Festival>()
        for (i in 0 until festivalsArray.length()) {
            festivalsList.add(jsonToFestival(festivalsArray.getJSONObject(i)))
        }
        return OdiaDayInfo(
            date = date,
            odiaMonth = odiaMonth,
            tithi = tithi,
            paksha = paksha,
            rashi = rashi,
            festivals = festivalsList
        )
    }

    fun festivalsListToJsonString(list: List<Festival>): String {
        val array = JSONArray()
        list.forEach { array.put(festivalToJson(it)) }
        return array.toString()
    }

    fun jsonStringToFestivalsList(jsonStr: String): List<Festival> {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<Festival>()
        for (i in 0 until array.length()) {
            list.add(jsonToFestival(array.getJSONObject(i)))
        }
        return list
    }

    fun odiaDayInfosListToJsonString(list: List<OdiaDayInfo>): String {
        val array = JSONArray()
        list.forEach { array.put(odiaDayInfoToJson(it)) }
        return array.toString()
    }

    fun jsonStringToOdiaDayInfosList(jsonStr: String): List<OdiaDayInfo> {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<OdiaDayInfo>()
        for (i in 0 until array.length()) {
            list.add(jsonToOdiaDayInfo(array.getJSONObject(i)))
        }
        return list
    }
}
