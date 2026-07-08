package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import java.io.File
import java.util.Locale

class StorageManagerCompat(context: Context) {
    private val storageManager = context.getSystemService<StorageManager>()!!
    class StorageVolumeCompat {
        val uuid: String?
        val state: String
        val directory: File?
        @RequiresApi(Build.VERSION_CODES.Q)
        val mediaStoreVolumeName: String?
        val isEmulated: Boolean
        @RequiresApi(Build.VERSION_CODES.N)
        val real: StorageVolume
        private val descriptionLegacy: String?

        constructor(real: StorageVolume) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                this.uuid = real.uuid
                this.isEmulated = real.isEmulated
                this.real = real
                this.state = real.state
                descriptionLegacy = null
            } else {
                this.uuid = getUuid.invoke(real) as String?
                this.state = getState.invoke(real) as String
                this.isEmulated = isEmulatedMethod.invoke(real) as Boolean
                @SuppressLint("NewApi")
                this.real = real
                this.descriptionLegacy = getUserLabel.invoke(real) as String?
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                this.directory = real.directory
                this.mediaStoreVolumeName = real.mediaStoreVolumeName
            } else {
                this.directory = if (state == Environment.MEDIA_MOUNTED ||
                    state == Environment.MEDIA_MOUNTED_READ_ONLY
                )
                    getPathFile.invoke(real) as File?
                else null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this.mediaStoreVolumeName = if (real.isPrimary) {
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    } else real.uuid?.lowercase(Locale.US)
                } else {
                    @SuppressLint("NewApi")
                    this.mediaStoreVolumeName = "external"
                }
            }
        }

        fun getDescription(context: Context): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                real.getDescription(context)
            } else {
                descriptionLegacy ?: @SuppressLint("NewApi")
                    getDescription.invoke(real, context) as String
            }
        }

        fun createOpenDocumentTreeIntent(): Intent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return real.createOpenDocumentTreeIntent()
            } else {
                val rootId = if (isEmulated) "primary" else uuid
                // AOSP uses root uri for Q+ but I found that only document uri works on older vers
                val rootUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "$rootId:"
                )
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri)
                } else { // try our luck
                    intent.putExtra("android.provider.extra.INITIAL_URI", rootUri)
                }
                // there seem to be both versions out there
                intent.putExtra("android.provider.extra.SHOW_ADVANCED", true)
                intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                return intent
            }
        }

        @RequiresApi(Build.VERSION_CODES.N)
        fun createAccessIntent(dir: String?): Intent? {
            @Suppress("deprecation")
            return real.createAccessIntent(dir)
        }
    }

    val storageVolumes: List<StorageVolumeCompat>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageManager.storageVolumes.map { StorageVolumeCompat(it) }
        } else {
            @Suppress("UNCHECKED_CAST")
            (getVolumeList.invoke(storageManager)!! as Array<StorageVolume>)
                .map { StorageVolumeCompat(it) }
        }

    companion object {
        private val getVolumeList by lazy {
            StorageManager::class.java.getMethod("getVolumeList")
        }
        private val getPathFile by lazy {
            @SuppressLint("NewApi")
            StorageVolume::class.java.getMethod("getPathFile")
        }
        private val getUuid by lazy {
            @SuppressLint("NewApi")
            StorageVolume::class.java.getMethod("getUuid")
        }
        private val getState by lazy {
            @SuppressLint("NewApi")
            StorageVolume::class.java.getMethod("getState")
        }
        private val isEmulatedMethod by lazy {
            @SuppressLint("NewApi")
            StorageVolume::class.java.getMethod("isEmulated")
        }
        private val getUserLabel by lazy {
            @SuppressLint("NewApi")
            StorageVolume::class.java.getMethod("getUserLabel")
        }
        private val getDescription by lazy {
            @SuppressLint("NewApi")
            StorageVolume::class.java.getMethod("getDescription",
                Context::class.java)
        }
    }
}