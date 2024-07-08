package io.github.kaii_lb.yamfsquared.manager.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.kaii_lb.yamfsquared.BuildConfig
import io.github.kaii_lb.yamfsquared.R
import io.github.kaii_lb.yamfsquared.common.getAttr
import io.github.kaii_lb.yamfsquared.common.runMain
import io.github.kaii_lb.yamfsquared.databinding.ActivityMainBinding
import io.github.kaii_lb.yamfsquared.manager.bases.BaseActivity
import io.github.kaii_lb.yamfsquared.manager.services.YAMFManagerProxy
import io.github.kaii_lb.yamfsquared.manager.ui.SettingsActivity
import io.github.kaii_lb.yamfsquared.manager.utils.TipUtil
import io.github.kaii_lb.yamfsquared.xposed.IOpenCountListener


class MainActivity: BaseActivity<ActivityMainBinding>(ActivityMainBinding::class.java, Config.NO_BACK),
    MenuProvider {
    companion object {
        const val TAG = "YAMFSquared_MainActivity"
    }

    private val openCountListener = object : IOpenCountListener.Stub() {
        override fun onUpdate(count: Int) {
            runMain {
                baseBinding.tvOpenCount.text = count.toString()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addMenuProvider(this, this)
        YAMFManagerProxy.registerOpenCountListener(openCountListener)
    }

    @SuppressLint("SetTextI18n")
    override fun initData() {
        val buildTime = YAMFManagerProxy.buildTime
        Log.d(TAG, "buildtime: $buildTime ${BuildConfig.BUILD_TIME}")
        when(buildTime) {
            0L -> {
                baseBinding.ivIcon.setImageResource(R.drawable.ic_error_outline_24)
                baseBinding.tvActive.setText(R.string.not_activated)
                baseBinding.tvVersion.text = ""
                val colorError = theme.getAttr(com.google.android.material.R.attr.colorError).data
                val colorOnError = theme.getAttr(com.google.android.material.R.attr.colorOnError).data
                baseBinding.mcvStatus.setCardBackgroundColor(colorError)
                baseBinding.mcvStatus.outlineAmbientShadowColor = colorError
                baseBinding.mcvStatus.outlineSpotShadowColor = colorError
                baseBinding.tvActive.setTextColor(colorOnError)
                baseBinding.tvVersion.setTextColor(colorOnError)
                baseBinding.mcvInfo.visibility = View.GONE
            }
            BuildConfig.BUILD_TIME -> {
                baseBinding.ivIcon.setImageResource(R.drawable.ic_round_check_circle_24)
                baseBinding.tvActive.setText(R.string.activated)
                baseBinding.tvVersion.text = "${YAMFManagerProxy.versionName} (${YAMFManagerProxy.versionCode})"
            }
            else -> {
                baseBinding.ivIcon.setImageResource(R.drawable.ic_warning_amber_24)
                baseBinding.tvActive.setText(R.string.need_reboot)
                baseBinding.tvVersion.text = "system: ${YAMFManagerProxy.versionName} (${YAMFManagerProxy.versionCode})\n" +
                        "module: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                baseBinding.mcvStatus.setCardBackgroundColor(MaterialColors.harmonizeWithPrimary(this, getColor(R.color.color_warning)))
                baseBinding.mcvStatus.setOnClickListener {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.need_reboot)
                        .setMessage(R.string.need_reboot_message)
                        .setPositiveButton(R.string.reboot) { _, _->
                            TipUtil.showTip(this, "do it yourself")
                        }
                        .show()
                }
            }
        }
        if (Build.VERSION.PREVIEW_SDK_INT != 0) {
            baseBinding.systemVersion.text = "${Build.VERSION.CODENAME} Preview (API ${Build.VERSION.SDK_INT})"
        } else {
            baseBinding.systemVersion.text = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        }
        baseBinding.tvBuildType.text = BuildConfig.BUILD_TYPE
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_main, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when(menuItem.itemId) {
            R.id.new_window -> {
                YAMFManagerProxy.createWindow()
                true
            }
            R.id.channel -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = "https://t.me/YAMF_channel".toUri()
                })
                true
            }
            R.id.open_app_list -> {
                YAMFManagerProxy.openAppList()
                true
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.github -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = "https://github.com/duzhaokun123/YAMF".toUri()
                })
                true
            }
            R.id.donate -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = "https://duzhaokun123.github.io/donate.html".toUri()
                })
                true
            }
            R.id.current_to_window -> {
                YAMFManagerProxy.currentToWindow()
                true
            }
            R.id.launch_side_bar -> {
                YAMFManagerProxy.launchSideBar()
                true
            }
            else -> false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        YAMFManagerProxy.unregisterOpenCountListener(openCountListener)
    }
}