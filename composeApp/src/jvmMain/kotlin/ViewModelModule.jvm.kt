import com.github.pplong.feat.home.viewmodel.HomeViewModel
import com.github.pplong.test.TestViewModel
import com.github.pplong.test.sftptest.SFTPTestViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val viewModelModule: Module = module {
    singleOf(::TestViewModel)
    factory { SFTPTestViewModel(get()) }

    factory { HomeViewModel(get()) }
}