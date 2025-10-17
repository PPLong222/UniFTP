package com.github.pplong

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.pplong.feat.browse.ui.BrowseScreen
import com.github.pplong.feat.home.ui.FTPServerItem
import com.github.pplong.feat.home.ui.HomeScreen
import com.github.pplong.test.TestScreen
import com.github.pplong.test.sftptest.SFTPScreen
import com.github.pplong.ui.theme.UniFTPTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App() {
    UniFTPTheme {

        val navController = rememberNavController()
        NavHost(
            navController = navController, startDestination = HomeScreenNav
        ) {
            composable<TestScreenNav> {
                TestScreen()
            }

            composable<SFTPScreenNav> {
                SFTPScreen()
            }

            composable<HomeScreenNav> {
                HomeScreen(navController)
            }

            composable<BrowseScreenNav> { entry ->
                val args = entry.toRoute<BrowseScreenNav>()
                BrowseScreen(
                    FTPServerItem(
                        id = 0,
                        host = args.host,
                        password = "",
                        user = args.user,
                        nickname = args.nickname,
                        lastConnectedTime = 0L,
                        port = 0,
                        downloadDir = args.downloadDir
                    ),
                    navController
                )
            }

        }
    }

}