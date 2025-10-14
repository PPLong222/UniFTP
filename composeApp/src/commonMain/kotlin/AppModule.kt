import com.github.pplong.feat.home.model.FTPServerDao
import com.github.pplong.test.TestDao
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule = module {
    single { "Hello World" }
}

val dataModule = module {
    single<TestDao> { get<AppDatabase>().getTestDao() }
    single<FTPServerDao> { get<AppDatabase>().getFTPServerDao() }
}

expect fun platformModule(): Module