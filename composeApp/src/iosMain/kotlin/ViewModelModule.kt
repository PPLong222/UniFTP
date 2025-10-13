import com.github.pplong.test.TestViewModel
import com.github.pplong.test.sftptest.SFTPTestViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val viewModelModule = module {
    singleOf(::TestViewModel)
    singleOf(::SFTPTestViewModel)
}