import com.github.pplong.sftp.SshjSftpClient
import com.github.pplong.sftp.api.IUSftpClient
import org.koin.dsl.module

val jvmModule = module {
    single<IUSftpClient> { SshjSftpClient() }
}