package io.github.kaii_lb.yamfsquared.xposed.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.args
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import io.github.kaii_lb.yamfsquared.BuildConfig
import io.github.kaii_lb.yamfsquared.common.gson
import io.github.kaii_lb.yamfsquared.common.model.Config
import io.github.kaii_lb.yamfsquared.common.model.StartCmd
import io.github.kaii_lb.yamfsquared.common.runMain
import io.github.kaii_lb.yamfsquared.manager.sidebar.SideBar
import io.github.kaii_lb.yamfsquared.xposed.IOpenCountListener
import io.github.kaii_lb.yamfsquared.xposed.IYAMFManager
import io.github.kaii_lb.yamfsquared.xposed.hook.HookLauncher
import io.github.kaii_lb.yamfsquared.xposed.ui.window.AppListWindow
import io.github.kaii_lb.yamfsquared.xposed.ui.window.AppWindow
import io.github.kaii_lb.yamfsquared.xposed.utils.Instances
import io.github.kaii_lb.yamfsquared.xposed.utils.Instances.systemContext
import io.github.kaii_lb.yamfsquared.xposed.utils.Instances.systemUiContext
import io.github.kaii_lb.yamfsquared.xposed.utils.createContext
import io.github.kaii_lb.yamfsquared.xposed.utils.getTopRootTask
import io.github.kaii_lb.yamfsquared.xposed.utils.log
import io.github.kaii_lb.yamfsquared.xposed.utils.registerReceiver
import io.github.kaii_lb.yamfsquared.xposed.utils.startAuto
import io.github.qauxv.ui.CommonContextWrapper
import rikka.hidden.compat.ActivityManagerApis
import java.io.File


object YAMFManager : IYAMFManager.Stub() {
    const val TAG = "YAMFManager"

    const val ACTION_GET_LAUNCHER_CONFIG = "io.github.kaii_lb.yamfsquared.ACTION_GET_LAUNCHER_CONFIG"
    const val ACTION_OPEN_APP = "io.github.kaii_lb.yamfsquared.action.OPEN_APP"
    const val ACTION_CURRENT_TO_WINDOW = "io.github.kaii_lb.yamfsquared.action.CURRENT_TO_WINDOW"
    const val ACTION_OPEN_APP_LIST = "io.github.kaii_lb.yamfsquared.action.OPEN_APP_LIST"
    const val ACTION_OPEN_IN_YAMF = "io.github.kaii_lb.yamfsquared.action.ACTION_OPEN_IN_YAMF"
    const val ACTION_LAUNCH_SIDE_BAR = "io.github.kaii_lb.yamfsquared.action.LAUNCH_SIDE_BAR"

    const val EXTRA_COMPONENT_NAME = "componentName"
    const val EXTRA_USER_ID = "userId"
    const val EXTRA_TASK_ID = "taskId"
    const val EXTRA_SOURCE = "source"

    const val SOURCE_UNSPECIFIED = 0
    const val SOURCE_RECENTS = 1
    const val SOURCE_TASKBAR = 2
    const val SOURCE_POPUP = 3

    val windowList = mutableListOf<Int>()
    lateinit var config: Config
    val configFile = File("/data/system/yamfsquared.json")
    var openWindowCount = 0
    val iOpenCountListenerSet = mutableSetOf<IOpenCountListener>()
    lateinit var activityManagerService: Any

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun systemReady() {
        Instances.init(activityManagerService)
        systemContext.registerReceiver(ACTION_OPEN_IN_YAMF, OpenInYAMFBroadcastReceiver)
        systemContext.registerReceiver(ACTION_CURRENT_TO_WINDOW) { _, _ ->
            currentToWindow()
        }
        systemContext.registerReceiver(ACTION_OPEN_APP_LIST) { _, _ ->
            AppListWindow(
                CommonContextWrapper.createAppCompatContext(systemUiContext.createContext()),
                null
            )
        }
        systemContext.registerReceiver(ACTION_OPEN_APP) { _, intent ->
            val componentName = intent.getParcelableExtra<ComponentName>(EXTRA_COMPONENT_NAME)
                ?: return@registerReceiver
            val userId = intent.getIntExtra(EXTRA_USER_ID, 0)
            createWindow(StartCmd(componentName = componentName, userId = userId))
        }
        systemContext.registerReceiver(ACTION_GET_LAUNCHER_CONFIG) { _, intent ->
            ActivityManagerApis.broadcastIntent(Intent(HookLauncher.ACTION_RECEIVE_LAUNCHER_CONFIG).apply {
                log(TAG, "send config: ${config.hookLauncher}")
                putExtra(HookLauncher.EXTRA_HOOK_RECENTS, config.hookLauncher.hookRecents)
                putExtra(HookLauncher.EXTRA_HOOK_TASKBAR, config.hookLauncher.hookTaskbar)
                putExtra(HookLauncher.EXTRA_HOOK_POPUP, config.hookLauncher.hookPopup)
                putExtra(HookLauncher.EXTRA_HOOK_TRANSIENT_TASKBAR, config.hookLauncher.hookTransientTaskbar)
                `package` = intent.getStringExtra("sender")
            }, 0)
        }
        systemContext.registerReceiver(ACTION_LAUNCH_SIDE_BAR) { _, _ ->
            SideBar(
                CommonContextWrapper.createAppCompatContext(systemUiContext.createContext()),
                null
            )
        }
        configFile.createNewFile()
        config = runCatching {
            gson.fromJson(configFile.readText(), Config::class.java)
        }.getOrNull() ?: Config()
        systemUiContext.startService(Intent(systemUiContext, SidebarService::class.java))
        log(TAG, "config: $config")
    }

    fun addWindow(id: Int) {
        windowList.add(0, id)
        openWindowCount++
        val toRemove = mutableSetOf<IOpenCountListener>()
        iOpenCountListenerSet.forEach {
            runCatching {
                it.onUpdate(openWindowCount)
            }.onFailure { _ ->
                toRemove.add(it)
            }
        }
        iOpenCountListenerSet.removeAll(toRemove)
    }

    fun removeWindow(id: Int) {
        windowList.remove(id)
    }

    fun isTop(id: Int) = windowList[0] == id

    fun moveToTop(id: Int) {
        windowList.remove(id)
        windowList.add(0, id)
    }

    fun createWindow(startCmd: StartCmd?) {
        Instances.iStatusBarService.collapsePanels()
        AppWindow(
            CommonContextWrapper.createAppCompatContext(systemUiContext.createContext()),
            config.densityDpi,
            config.flags
        ) { displayId ->
            addWindow(displayId)
            startCmd?.startAuto(displayId)
        }
    }

    init {
        log(TAG, "YAMFSquared service initialized")
    }

    override fun getVersionName(): String {
        return BuildConfig.VERSION_NAME
    }

    override fun getVersionCode(): Int {
        return BuildConfig.VERSION_CODE
    }

    override fun getUid(): Int {
        return Process.myUid()
    }

    override fun createWindow() {
        runMain {
            createWindow(null)
        }
    }

    override fun getBuildTime(): Long {
        return BuildConfig.BUILD_TIME
    }

    override fun getConfigJson(): String {
        return gson.toJson(config)
    }

    override fun updateConfig(newConfig: String) {
        config = gson.fromJson(newConfig, Config::class.java)
        runMain {
            configFile.writeText(newConfig)
            Log.d(TAG, "updateConfig: $config")
        }
    }

    override fun registerOpenCountListener(iOpenCountListener: IOpenCountListener) {
        iOpenCountListenerSet.add(iOpenCountListener)
        iOpenCountListener.onUpdate(openWindowCount)
    }

    override fun unregisterOpenCountListener(iOpenCountListener: IOpenCountListener?) {
        iOpenCountListenerSet.remove(iOpenCountListener)
    }

    override fun openAppList() {
        runMain {
            Instances.iStatusBarService.collapsePanels()
            AppListWindow(
                CommonContextWrapper.createAppCompatContext(systemUiContext.createContext()),
                null
            )
        }
    }

    override fun currentToWindow() {
        runMain {
            val task = getTopRootTask(0) ?: return@runMain

            if (task.baseActivity?.packageName != "com.android.launcher3") {
                createWindow(StartCmd(taskId = task.taskId))
            }
        }
    }

    override fun resetAllWindow() {
        runMain {
            Instances.iStatusBarService.collapsePanels()
            systemContext.sendBroadcast(Intent(AppWindow.ACTION_RESET_ALL_WINDOW))
        }
    }

    override fun launchSideBar() {
        runMain {
            SideBar(
                CommonContextWrapper.createAppCompatContext(systemUiContext.createContext()),
                null
            )
        }
    }

    private val OpenInYAMFBroadcastReceiver: BroadcastReceiver.(Context, Intent) -> Unit =
        { _: Context, intent: Intent ->
            val taskId = intent.getIntExtra(EXTRA_TASK_ID, 0)
            val componentName = intent.getParcelableExtra<ComponentName>(EXTRA_COMPONENT_NAME)
            val userId = intent.getIntExtra(EXTRA_USER_ID, 0)
            val source = intent.getIntExtra(EXTRA_SOURCE, SOURCE_UNSPECIFIED)
            createWindow(StartCmd(componentName, userId, taskId))

            // TODO: better way to close recents
            if (source == SOURCE_RECENTS && config.recentsBackHome) {
                val down = KeyEvent(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_HOME,
                    0
                ).apply {
                    this.source = InputDevice.SOURCE_KEYBOARD
                    this.invokeMethod("setDisplayId", args(0), argTypes(Integer.TYPE))
                }
                Instances.inputManager.injectInputEvent(down, 0)
                val up = KeyEvent(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_HOME,
                    0
                ).apply {
                    this.source = InputDevice.SOURCE_KEYBOARD
                    this.invokeMethod("setDisplayId", args(0), argTypes(Integer.TYPE))
                }
                Instances.inputManager.injectInputEvent(up, 0)
            }
        }
}