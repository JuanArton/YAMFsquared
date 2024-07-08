package io.github.kaii_lb.yamfsquared.xposed.ui.window

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.IPackageManagerHidden
import android.content.pm.PackageManagerHidden
import android.content.pm.UserInfo
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doOnTextChanged
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.args
import com.github.kyuubiran.ezxhelper.utils.invokeMethodAs
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import io.github.kaii_lb.yamfsquared.common.model.StartCmd
import io.github.kaii_lb.yamfsquared.common.onException
import io.github.kaii_lb.yamfsquared.common.resetAdapter
import io.github.kaii_lb.yamfsquared.common.runIO
import io.github.kaii_lb.yamfsquared.common.runMain
import io.github.kaii_lb.yamfsquared.databinding.ItemAppBinding
import io.github.kaii_lb.yamfsquared.databinding.WindowAppListBinding
import io.github.kaii_lb.yamfsquared.manager.bases.BaseSimpleAdapter
import io.github.kaii_lb.yamfsquared.manager.sidebar.SideBar
import io.github.kaii_lb.yamfsquared.xposed.services.YAMFManager
import io.github.kaii_lb.yamfsquared.xposed.ui.window.model.AppInfo
import io.github.kaii_lb.yamfsquared.xposed.utils.AppInfoCache
import io.github.kaii_lb.yamfsquared.xposed.utils.Instances
import io.github.kaii_lb.yamfsquared.xposed.utils.TipUtil
import io.github.kaii_lb.yamfsquared.xposed.utils.componentName
import io.github.kaii_lb.yamfsquared.xposed.utils.getActivityInfoCompat
import io.github.kaii_lb.yamfsquared.xposed.utils.log
import io.github.kaii_lb.yamfsquared.xposed.utils.startActivity
import java.util.Locale

@SuppressLint("ClickableViewAccessibility")
class AppListWindow(val context: Context, val displayId: Int? = null) {
    companion object {
        const val TAG = "YAMF_AppListWindow"
    }

    private lateinit var binding: WindowAppListBinding
    private val users = mutableMapOf<Int, String>()
    var userId = 0
    private var apps = emptyList<ActivityInfo>()
    var showApps: MutableList<AppInfo> = mutableListOf()

    init {
        runCatching {
            binding = WindowAppListBinding.inflate(LayoutInflater.from(context))
        }.onException { e ->
            Log.e(TAG, "new app list failed: ", e)
            TipUtil.showToast("new app list failed\nmay you forget reboot")
        }.onSuccess {
            doInit()
        }
    }

    private fun doInit() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        binding.root.let { layout ->
            Instances.windowManager.addView(layout, params)
        }
        binding.root.setOnClickListener {
            close()
        }
        binding.mcv.setOnTouchListener { _, _ -> true }
        Instances.userManager.invokeMethodAs<List<UserInfo>>(
            "getUsers",
            args(true, true, true),
            argTypes(java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
        )!!
            .filter { it.isProfile || it.isPrimary }
            .forEach {
                users[it.id] = it.name
            }

        log(SideBar.TAG, users.toString())
        binding.btnUser.setOnClickListener {
            PopupMenu(context, binding.btnUser).apply {
                users.forEach { (t, u) ->
                    menu.add(u).setOnMenuItemClickListener {

                        onSelectUser(t)
                        true
                    }
                }
            }.show()
        }
        onSelectUser(0)
        binding.rv.layoutManager = FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }
        binding.rv.adapter = Adapter()

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            text ?: return@doOnTextChanged
            val filteredApps = apps.filter { activityInfo ->
                text in activityInfo.packageName ||
                        AppInfoCache.getIconLabel(activityInfo).second.contains(text, true)
            }
            showApps.clear()
            filteredApps.forEach{ activityInfo ->
                val appInfoCache = AppInfoCache.getIconLabel(activityInfo)
                showApps.add(
                    AppInfo(
                        0, appInfoCache.first, appInfoCache.second, activityInfo.componentName, userId
                    )
                )
                showApps.sortBy { it.label.toString().lowercase(Locale.ROOT) }
            }
            binding.rv.resetAdapter()
        }
    }

    private fun onSelectUser(userId: Int) {
        binding.pv.visibility = View.VISIBLE
        this.userId = userId
        binding.btnUser.text = users[userId]

        runIO {
            apps = (Instances.packageManager as PackageManagerHidden).queryIntentActivitiesAsUser(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }, 0, userId
            ).map {
                (Instances.iPackageManager as IPackageManagerHidden).getActivityInfoCompat(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    0, userId
                )
            }
            apps.forEach { activityInfo ->
                val appInfoCache = AppInfoCache.getIconLabel(activityInfo)
                showApps.add(
                    AppInfo(
                        0, appInfoCache.first, appInfoCache.second, activityInfo.componentName, userId
                    )
                )
            }
            showApps.sortBy { it.label.toString().lowercase(Locale.ROOT) }
            runMain {
                binding.etSearch.text.clear()
                binding.rv.resetAdapter()
                binding.pv.visibility = View.GONE
            }
        }
    }

    private fun close() {
        Instances.windowManager.removeView(binding.root)
    }

    inner class Adapter : BaseSimpleAdapter<ItemAppBinding>(context, ItemAppBinding::class.java) {
        override fun initViews(baseBinding: ItemAppBinding, position: Int) {
            val activityInfo = showApps[position]

            baseBinding.ll.setOnClickListener {
                if (displayId == null)
                    YAMFManager.createWindow(StartCmd(activityInfo.componentName, userId))
                else
                    startActivity(context, activityInfo.componentName, userId, displayId)
                close()
            }
        }

        override fun initData(baseBinding: ItemAppBinding, position: Int) {
            val activityInfo = showApps[position]
            val icon = activityInfo.icon
            val label = activityInfo.label
            baseBinding.ivIcon.setImageDrawable(icon)
            baseBinding.tvLabel.text = label
        }

        override fun getItemCount() = showApps.size
    }
}