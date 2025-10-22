import com.github.pplong.feat.home.ui.FTPServerItem
import com.github.pplong.feat.home.viewmodel.HomeViewModel
import com.github.pplong.test.TestViewModel
import com.github.pplong.test.sftptest.SFTPTestViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

actual val viewModelModule = module {
    viewModelOf(::TestViewModel)
    viewModelOf(::SFTPTestViewModel)
    viewModelOf(::HomeViewModel)
    viewModel { (server: FTPServerItem) -> BrowseViewModel(server) }
}