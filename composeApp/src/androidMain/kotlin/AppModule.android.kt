import androidx.work.WorkerParameters
import com.github.pplong.feat.transfer.AndroidTransferManager
import com.github.pplong.feat.transfer.ProgressMonitor
import com.github.pplong.feat.transfer.TransferWorker
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    // Database
    single<AppDatabase> {
        getRoomDatabase(getDatabaseBuilder(get()))
    }

    // Transfer Manager
    single { AndroidTransferManager(get()) }

    // Progress Monitor
    single { ProgressMonitor(get(), get()) }

    // WorkManager Workers - Koin automatically provides Context and WorkerParameters
    // We only need to inject our custom dependencies
    worker { TransferWorker(get(), get(), get<AppDatabase>().getTransferTaskDao()) }
}