import com.github.pplong.TestLibssh2SftpClient
import com.github.pplong.feat.transfer.IosTransferManager
import com.github.pplong.feat.transfer.ProgressMonitor
import com.github.pplong.sftp.api.IUSftpClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    // Database
    single<AppDatabase> {
        getRoomDatabase(getDatabaseBuilder())
    }

    single<IUSftpClient> { TestLibssh2SftpClient() }

    // Transfer Manager
    single { IosTransferManager() }

    // Progress Monitor
    single { ProgressMonitor() }
}