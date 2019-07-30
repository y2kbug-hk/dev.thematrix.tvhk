package dev.thematrix.tvhk

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.NoCache
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import java.io.File
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLContext

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ctx = this

        setupNetwork()
        initialize()
    }

    private fun setupNetwork(){
        try {
            ProviderInstaller.installIfNeeded(applicationContext)
            var sslContext: SSLContext? = null
            sslContext = SSLContext.getInstance("TLSv1.2")
            try {
                sslContext!!.init(null, null, null)
                val engine = sslContext.createSSLEngine()
                engine.enabledCipherSuites
            } catch (e: KeyManagementException) {
            }
        } catch (e: NoSuchAlgorithmException) {
        } catch (e: GooglePlayServicesNotAvailableException) {
            hasPlaystore = false
        } catch (e: GooglePlayServicesRepairableException) {
            hasPlaystore = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            permissionRequestCode -> {
                if (grantResults.isEmpty() || grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkUpdate()
                }
            }
        }
    }

    private fun checkUpdate(){
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            "https://thematrix.dev/tvhk/release.json",
            null,
            Response.Listener { response ->
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val versionCode = packageInfo.versionCode

                if(response.getInt("version") > versionCode){
                    val builder = AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog_Alert)
                    builder.setTitle("更新到 v" + response.getString("version") + " ?")
                    builder.setMessage(response.getString("note"))

                    builder.setPositiveButton(android.R.string.yes) { dialog, which ->
                        downloadUpdate()
                    }

                    builder.setNegativeButton(android.R.string.no) { dialog, which ->
                    }

                    builder.show()
                }else{
                    initialize()
                }
            },
            Response.ErrorListener{ error ->
            }
        )

        val requestQueue = RequestQueue(NoCache(), BasicNetwork(HurlStack())).apply {
            start()
        }

        requestQueue.add(jsonObjectRequest)
    }

    private fun downloadUpdate() {
        registerReceiver(onDownloadComplete(), IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        val request = DownloadManager
            .Request(Uri.parse("https://thematrix.dev/tvhk/app-release.apk"))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "tvhk.apk")

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)
    }

    private class onDownloadComplete: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val c = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            if(c != null){
                c.moveToFirst()
                val fileUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                val mFile = File(Uri.parse(fileUri).path!!)
                val fileName = mFile.absolutePath

                context.unregisterReceiver(this)

                val intent = Intent(Intent.ACTION_VIEW)
                var contentUri: Uri
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileProvider", File(fileName))

                }else{
                    contentUri = Uri.fromFile(File(fileName))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
                startActivity(ctx, intent, null)
            }
        }
    }

    private fun initialize() {
        lastShownDialog++

        if (lastShownDialog == 0) {
            if(hasPlaystore) {
                initialize()
            }else{
                val PERM_READ_EXTERNAL_STORAGE = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                val PERM_WRITE_EXTERNAL_STORAGE = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

                if (PERM_READ_EXTERNAL_STORAGE == PackageManager.PERMISSION_GRANTED && PERM_WRITE_EXTERNAL_STORAGE == PackageManager.PERMISSION_GRANTED) {
                    hasPermission = true
                    checkUpdate()
                }else{
                    val builder = AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog_Alert)
                    builder.setTitle("TVHK")
                    builder.setMessage("由於系統沒有Google Play Service，需要額外權限下載更新")

                    builder.setPositiveButton("授權下載更新") { dialog, which ->
                        ActivityCompat.requestPermissions(this, arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ), permissionRequestCode)
                    }

                    builder.setNegativeButton("不了") { dialog, which ->
                        initialize()
                    }

                    builder.show()
                }
            }
        }else if (lastShownDialog == 1) {
            playerType = SharedPreference(this).getInt("playerType")
            if (playerType == -1) {
                val builder = AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog_Alert)
                builder.setTitle("選擇播放器")
                builder.setMessage("內置播放器：可用方向掣轉台、載入速度較快\n" +
                        "\n" +
                        "外置播放器：可用幾乎任何嘅播放器。建議使用MX Player\n" +
                        "\n" +
                        "注意：Android 4用家如內置播放器唔正常運作，請揀外置播放器\n" +
                        "\n" +
                        "建議：先唔選擇，使用預設內置播放器，用唔到先再揀外置播放器"
                )

                builder.setPositiveButton("內置播放器") { dialog, which ->
                    playerType = playerUseInternal
                    SharedPreference(this).saveInt("playerType", playerType)
                    initialize()
                }

                builder.setNegativeButton("外置播放器") { dialog, which ->
                    playerType = playerUseExternal
                    SharedPreference(this).saveInt("playerType", playerType)
                    initialize()
                }

                builder.setNeutralButton("遲下再講") { dialog, which ->
                    playerType = playerUseUnknown
                    SharedPreference(this).saveInt("playerType", playerType)
                    initialize()
                }

                builder.show()
            }
        } else if (lastShownDialog == 2) {
            copyUrlToClipboard = SharedPreference(this).getInt("copyUrlToClipboard")
            if (copyUrlToClipboard == -1) {
                val builder = AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog_Alert)
                builder.setTitle("複製播放網址")
                builder.setMessage("你想每次播放都複製播放網址到剪貼簿嗎？")

                builder.setPositiveButton("好") { dialog, which ->
                    copyUrlToClipboard = doCopyUrlToClipboard
                    SharedPreference(this).saveInt("copyUrlToClipboard", copyUrlToClipboard)
                    initialize()
                }

                builder.setNegativeButton("不了") { dialog, which ->
                    copyUrlToClipboard = dontCopyUrlToClipboard
                    SharedPreference(this).saveInt("copyUrlToClipboard", copyUrlToClipboard)
                    initialize()
                }

                builder.show()
            }
        }
    }

    companion object {
        lateinit var ctx: Context

        private var hasPlaystore: Boolean = true
        private val permissionRequestCode = 689777
        private var hasPermission: Boolean = false
        private lateinit var downloadManager: DownloadManager
        private var downloadId: Long = -1

        var lastShownDialog: Int = -1

        var playerType: Int = -1
        val playerUseUnknown: Int = -1
        val playerUseInternal: Int = 0
        val playerUseExternal: Int = 1

        var copyUrlToClipboard: Int = -1
        val doCopyUrlToClipboard: Int = 0
        val dontCopyUrlToClipboard: Int = 1
    }
}
