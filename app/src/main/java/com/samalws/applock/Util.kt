package com.samalws.applock

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec


class Util {
    companion object {
        fun currentlyInstalledApps(context: Context) : List<ApplicationInfo> {
            val pkgMgr = context.packageManager

            @SuppressLint("QueryPermissionsNeeded")
            // warning is fine, I added permission to manifest
            val apps = pkgMgr.getInstalledApplications(PackageManager.GET_META_DATA)

            return apps.filter {
                pkgMgr.getLaunchIntentForPackage(it.packageName) != null
            }
        }
        fun nameOfApp(context: Context, appInfo: ApplicationInfo) : String {
            val pkgMgr = context.packageManager
            return appInfo.loadLabel(pkgMgr).toString()
        }
        fun getAppPkgNameList(context: Context) : Set<String> =
            (currentlyInstalledApps(context).map { it.packageName }).toSet()

        fun goToHomeScreen(ctx: Context) {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun hashString(str: String, salt: Long) : String {
            if (str == "")
                return ""

            // iteration count chosen by experimentation so computation time takes 100ms
            val saltAsBytes = ByteBuffer
                .allocate(Long.SIZE_BYTES / Byte.SIZE_BYTES)
                .putLong(salt)
                .array()
            val hashInputs = PBEKeySpec(
                str.toCharArray(),
                saltAsBytes,
                10000,
                512
            )
            val hashFunc = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256")
            val hashedAsKey = hashFunc.generateSecret(hashInputs)
            val hashedAsArr = hashedAsKey.encoded
            return Base64.encodeToString(hashedAsArr, Base64.NO_WRAP or Base64.NO_PADDING or Base64.NO_CLOSE)
        }

        private fun getSalt(sp: SharedPreferences): Long =
            sp.getLong("passSalt", 0)
        fun getPasswordSet(sp: SharedPreferences): Boolean =
            getSalt(sp) != 0L
        fun passwordCorrect(sp: SharedPreferences, password: String) : Boolean {
            val expectedVal = sp.getString("passHash", "")
            val salt = getSalt(sp)
            val passHashed = hashString(password, salt)
            return expectedVal != "" && expectedVal != null && passHashed == expectedVal
        }
        fun setPassword(sp: SharedPreferences, password: String) {
            var salt = 0L
            while (salt == 0L) // generate nonzero salt
                salt = SecureRandom().nextLong()

            sp.edit()
                .putString("passHash", hashString(password, salt))
                .putLong("passSalt", salt)
                .apply()
        }
        fun getAppBlocked(sp: SharedPreferences, id: String) : Boolean =
            sp.getBoolean("appBlocked \"$id\"", false)
        fun setAppBlocked(sp: SharedPreferences, id: String, block: Boolean) {
            sp.edit().putBoolean("appBlocked \"$id\"", block).apply()
        }
        fun getLockNum(sp: SharedPreferences): Long =
            sp.getLong("lockNum", 0L)
        fun getLockStatus(sp: SharedPreferences): Boolean =
            getLockNum(sp) % 2 == 0L // even lockNum: lock is on; odd: lock is off
        fun setLockStatus(sp: SharedPreferences, status: Boolean) {
            if (status == getLockStatus(sp))
                return
            sp.edit().putLong("lockNum", getLockNum(sp) + 1).apply()
        }

        fun makeToast(context: Context, resource: Int) {
            Toast.makeText(context, resource, Toast.LENGTH_SHORT).show()
        }
        fun makeAlert(
            context: Context, title: Int, enterButton: Int,
            enterButtonFunc: () -> Unit = {}, extraContent: View? = null,
        ): AlertDialog {
            val titleView = View.inflate(context, R.layout.alert_title, null)
            titleView.findViewById<TextView>(R.id.alert_title).setText(title)

            val builder = AlertDialog.Builder(context)
                .setCustomTitle(titleView)
                .setPositiveButton(enterButton) { _, _ -> enterButtonFunc() }

            extraContent?.let { builder.setView(it) }

            val retVal = builder.create()
            retVal.show()
            return retVal
        }

        fun listenForGo(input: EditText, f: () -> Unit) {
            input.setOnEditorActionListener { _, actionId, _ ->
                val rightId = actionId == EditorInfo.IME_ACTION_GO
                if (rightId) f()
                rightId
            }
        }
    }
}