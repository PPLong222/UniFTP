package com.github.pplong

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.pplong.feat.browse.ui.BrowseScreen
import com.github.pplong.feat.home.ui.HomeScreen
import com.github.pplong.test.TestScreen
import com.github.pplong.test.sftptest.SFTPScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App() {
    MaterialExpressiveTheme {

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

            composable<BrowseScreenNav> {
                BrowseScreen()
            }
        }
    }
}