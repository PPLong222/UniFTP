import com.github.pplong.feat.home.viewmodel.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharedViewModelModule = module {
    viewModel { HomeViewModel(get(), get(), get()) }
}