import com.github.pplong.sftp.TestSshjSftpClient
import com.github.pplong.sftp.api.IUSftpClient
import org.koin.dsl.module

val jvmModule = module {
    single<IUSftpClient> { TestSshjSftpClient() }
}