package com.deepfocus.app.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.deepfocus.app.MainActivity
import com.deepfocus.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大肥鱼的主动通知
 *
 * 用户要求（2026-11-18）：
 * > 「注意大肥鱼唤醒时主动发的消息可以通过通知栏出现，
 * > 所以用户不在 app 内也能收到大肥鱼的消息。」
 *
 * 之前 App **完全没有业务通知** ——
 * 只有前台服务那条 IMPORTANCE_MIN 的常驻通知（用户根本看不见）。
 * 也就是说大肥鱼「主动冒泡」只在 App 开着的时候有效，
 * 用户切走了她就哑了。
 *
 * 现在有了这个：她主动说话时，除了写进聊天记录，还会**弹一条通知**。
 * 点通知直接进 App。
 */
@Singleton
class FishNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: android.content.SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 发一条主动通知
     *
     * 没给通知权限就静默跳过 —— 不弹权限对话框，
     * 用户没授权是她自己的选择，不该反复骚扰。
     *
     * @param title 标题（通常是「大肥鱼」）
     * @param body 正文（她要说的话）
     * @param id 通知 ID，同 ID 会覆盖旧的（避免刷屏）
     * @return true 表示通知真的发出去了；false 表示被跳过（没权限 / 内容空）
     */
    fun notifyProactive(title: String, body: String, id: Int = NOTIFICATION_ID_PROACTIVE): Boolean {
        if (!canPostNotifications()) {
            android.util.Log.w(TAG, "没有通知权限，跳过")
            return false
        }
        if (body.isBlank()) {
            android.util.Log.w(TAG, "内容为空，跳过")
            return false
        }

        val manager = NotificationManagerCompat.from(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROACTIVE)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openAppIntent())
            .build()

        return try {
            manager.notify(id, notification)
            android.util.Log.i(TAG, "已发通知 id=$id: $body")
            true
        } catch (e: SecurityException) {
            // 权限在通知构建后才被撤销，忽略即可
            android.util.Log.w(TAG, "发通知被拒: ${e.message}")
            false
        }
    }

    /** 点通知 → 打开 App */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /** 用户给了通知权限吗 */
    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 今天催过待办了没
     *
     * 不记的话，Worker 每 15 分钟醒一次就会催一次同样的事 ——
     * 那不是督促，是骚扰。
     */
    fun wasTodoNagSentToday(): Boolean {
        return prefs.getString(KEY_TODO_NAG_DAY, null) == todayKey()
    }

    /** 记下今天催过了 */
    fun markTodoNagSentToday() {
        prefs.edit().putString(KEY_TODO_NAG_DAY, todayKey()).apply()
    }

    private fun todayKey(): String {
        val c = java.util.Calendar.getInstance()
        return String.format(
            "%04d%02d%02d",
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    companion object {
        private const val TAG = "FishNotifier"

        /** 主动消息的渠道 ID */
        const val CHANNEL_ID_PROACTIVE = "deep_focus_proactive"

        /** 主动消息的固定通知 ID（同 ID 覆盖，不刷屏） */
        const val NOTIFICATION_ID_PROACTIVE = 2001

        /**
         * 待办催办的通知 ID
         *
         * 跟普通主动消息分开，这样她能同时留一条「随口说的」和一条「催待办的」；
         * 也方便用户单独划掉其中一条。
         */
        const val NOTIFICATION_ID_TODO_NAG = 2002

        /** 记录「今天催过待办」的 SharedPreferences */
        private const val PREFS_NAME = "deep_focus_notifier"
        private const val KEY_TODO_NAG_DAY = "todo_nag_day"

        /**
         * 创建主动消息渠道
         *
         * 跟前台服务那条常驻通知分开 ——
         * 那条是 IMPORTANCE_MIN（用户看不见），
         * 这条必须是 DEFAULT，不然用户还是收不到。
         */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID_PROACTIVE,
                "大肥鱼的消息",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "大肥鱼主动找你说话时的提醒"
                setShowBadge(true)
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
