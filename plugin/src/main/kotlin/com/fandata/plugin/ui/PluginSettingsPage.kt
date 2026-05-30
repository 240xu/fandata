package com.fandata.plugin.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fandata.plugin.BookSourceManager
import io.nightfish.lightnovelreader.api.ui.components.SettingsClickableEntry
import io.nightfish.lightnovelreader.api.ui.components.SettingsSwitchEntry
import io.nightfish.lightnovelreader.api.userdata.UserDataRepositoryApi

@Composable
fun PluginSettingsPage(paddingValues: PaddingValues, userDataRepo: UserDataRepositoryApi) {
    val context = LocalContext.current
    Column(
        Modifier
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 书源信息
        val src = BookSourceManager.getCurrent()
        if (src != null) {
            Card(
                Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前书源", style = MaterialTheme.typography.titleSmall)
                    Text(src.bookSourceName, style = MaterialTheme.typography.bodyLarge)
                    Text(src.bookSourceUrl, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("共 ${BookSourceManager.getAll().size} 个书源可用",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("功能开关", modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleSmall)
        // 段评
        val pr by userDataRepo.booleanUserData("paragraph_review")
            .getFlowWithDefault(false).collectAsState(false)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "段落评论",
            description = "在正文中显示段落评论标记",
            checked = pr,
            booleanUserData = userDataRepo.booleanUserData("paragraph_review")
        )
        // 书架同步
        val bs by userDataRepo.booleanUserData("bookshelf_sync")
            .getFlowWithDefault(true).collectAsState(true)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "书架同步",
            description = "自动同步阅读进度到服务器",
            checked = bs,
            booleanUserData = userDataRepo.booleanUserData("bookshelf_sync")
        )
        // 阅读提示
        val rt by userDataRepo.booleanUserData("reading_toast")
            .getFlowWithDefault(false).collectAsState(false)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "阅读提示",
            description = "显示操作的 Toast 提示",
            checked = rt,
            booleanUserData = userDataRepo.booleanUserData("reading_toast")
        )
        // 显示来源
        val ss by userDataRepo.booleanUserData("show_source")
            .getFlowWithDefault(false).collectAsState(false)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "显示来源",
            description = "在目录中显示章节来源",
            checked = ss,
            booleanUserData = userDataRepo.booleanUserData("show_source")
        )
        Spacer(Modifier.height(8.dp))
        Text("账号与操作", modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleSmall)
        // 登录按钮
        SettingsClickableEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "登录书源账号",
            description = if (src != null) "登录 ${src.bookSourceName}" else "请先加载书源"
        ) {
            val intent = Intent(context, LoginActivity::class.java)
            intent.putExtra("sourceUrl", src?.bookSourceUrl ?: "")
            context.startActivity(intent)
        }
        // 导入书源
        SettingsClickableEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "导入书源",
            description = "从 JSON 文件导入 Legado 书源"
        ) {
            Toast.makeText(context, "将书源 JSON 放到 /Documents/fandata/ 目录后重启插件",
                Toast.LENGTH_LONG).show()
        }
        // 重置
        SettingsClickableEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "重置设置",
            description = "恢复所有功能开关为默认值"
        ) {
            userDataRepo.booleanUserData("paragraph_review").set(false)
            userDataRepo.booleanUserData("bookshelf_sync").set(true)
            userDataRepo.booleanUserData("reading_toast").set(false)
            userDataRepo.booleanUserData("show_source").set(false)
            Toast.makeText(context, "设置已重置", Toast.LENGTH_SHORT).show()
        }
    }
}