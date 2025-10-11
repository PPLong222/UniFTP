import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

// shared/src/jvmMain/kotlin/Database.desktop.kt

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "my_room.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}