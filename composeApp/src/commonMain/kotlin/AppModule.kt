import com.github.pplong.test.TestDao
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule = module {
    single { "Hello World" }
}

val dataModule = module {
    single<TestDao> { get<AppDatabase>().getTestDao() }
}

expect fun platformModule(): Module