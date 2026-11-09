package com.sarahpena.rutasmart.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sarahpena.rutasmart.domain.model.ConsultaHistorial
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.TipoMedio
import kotlinx.coroutines.flow.Flow

/** Tabla «consultas»: historial de búsquedas. */
@Entity(tableName = "consultas")
data class ConsultaEntity(
    @PrimaryKey val id: String,
    val usuarioId: String,
    val origen: String,
    val destino: String,
    val fechaMillis: Long,
    val recomendada: String,
    val puntaje: Double,
    val tiempoMinutos: Int,
    val costo: Int,
    val alternativas: Int,
    val criterios: String,
    val sincronizada: Boolean
) {
    fun aDominio() = ConsultaHistorial(id, origen, destino, fechaMillis, recomendada, puntaje, tiempoMinutos, costo, alternativas, criterios, sincronizada)

    companion object {
        fun desde(c: ConsultaHistorial, usuarioId: String) = ConsultaEntity(
            c.id, usuarioId, c.origen, c.destino, c.fechaMillis, c.recomendada, c.puntaje,
            c.tiempoMinutos, c.costo, c.alternativas, c.criterios, c.sincronizada
        )
    }
}

/** Tabla «preferencias»: criterios del usuario (una fila por usuario). */
@Entity(tableName = "preferencias")
data class PreferenciasEntity(
    @PrimaryKey val usuarioId: String,
    val tiempo: Int,
    val costo: Int,
    val comodidad: Int,
    val transbordos: Int,
    val ambiental: Int,
    val horaPico: Boolean,
    val medios: String // nombres de TipoMedio separados por coma
) {
    fun aDominio() = CriteriosUsuario(
        tiempo, costo, comodidad, transbordos, ambiental, horaPico,
        medios.split(',').mapNotNull { n -> TipoMedio.entries.firstOrNull { it.name == n.trim() } }.toSet()
    )

    companion object {
        fun desde(c: CriteriosUsuario, usuarioId: String) = PreferenciasEntity(
            usuarioId, c.tiempo, c.costo, c.comodidad, c.transbordos, c.ambiental, c.horaPico,
            c.mediosPermitidos.joinToString(",") { it.name }
        )
    }
}

@Dao
interface ConsultaDao {
    @Query("SELECT * FROM consultas WHERE usuarioId = :usuarioId ORDER BY fechaMillis DESC")
    fun observar(usuarioId: String): Flow<List<ConsultaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(consulta: ConsultaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodas(consultas: List<ConsultaEntity>)

    @Query("SELECT * FROM consultas WHERE usuarioId = :usuarioId AND sincronizada = 0")
    suspend fun pendientes(usuarioId: String): List<ConsultaEntity>

    @Query("UPDATE consultas SET sincronizada = 1 WHERE id = :id")
    suspend fun marcarSincronizada(id: String)

    @Query("DELETE FROM consultas WHERE id = :id")
    suspend fun eliminar(id: String)

    @Query("DELETE FROM consultas WHERE usuarioId = :usuarioId")
    suspend fun eliminarTodas(usuarioId: String)
}

@Dao
interface PreferenciasDao {
    @Query("SELECT * FROM preferencias WHERE usuarioId = :usuarioId")
    fun observar(usuarioId: String): Flow<PreferenciasEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(preferencias: PreferenciasEntity)
}

@Database(entities = [ConsultaEntity::class, PreferenciasEntity::class], version = 1, exportSchema = false)
abstract class RutaSmartDatabase : RoomDatabase() {
    abstract fun consultaDao(): ConsultaDao
    abstract fun preferenciasDao(): PreferenciasDao

    companion object {
        fun crear(context: Context): RutaSmartDatabase =
            Room.databaseBuilder(context.applicationContext, RutaSmartDatabase::class.java, "rutasmart.db").build()
    }
}
