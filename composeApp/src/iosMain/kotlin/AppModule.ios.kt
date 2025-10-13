import com.github.pplong.Libssh2SftpClient
import com.github.pplong.sftp.api.IUSftpClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<AppDatabase> {
        getRoomDatabase(getDatabaseBuilder())
    }

    single<IUSftpClient> { Libssh2SftpClient() }

}