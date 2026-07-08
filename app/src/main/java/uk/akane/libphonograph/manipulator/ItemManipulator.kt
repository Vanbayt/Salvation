package uk.akane.libphonograph.manipulator

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.UriPermission
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.provider.DocumentsContractCompat
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.hasMarkIsFavouriteStatus
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.utils.StorageManagerCompat
import org.akanework.gramophone.ui.MainActivity
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.getStringOrNullIfThrow
import uk.akane.libphonograph.toUriCompat
import java.io.File
import java.io.IOException

object ItemManipulator {
    private const val TAG = "ItemManipulator"
    // TODO: generally migrate writing IO to MediaStore on R+ (not on legacy!) in order to get rich
    //  error messages instead of FUSE just saying ENOPERM (reading too, but it's complicated
    //  because of pending-by-FUSE handling)
    //  i.e. do not use FUSE for insert, delete, or anything else
    //  also fallback to ContentProvider.delete() with RecoverableSecurityException if
    //   createDeleteRequest throws the all items need to be specified unique id stuff

    suspend fun deleteSong(context: MainActivity, file: File, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri("external"), id
        )
        val uris = mutableSetOf(uri)
        // TODO maybe don't hardcode these extensions twice, here and in LrcUtils?
        uris.addAll(setOf("ttml", "lrc", "srt").map {
            file.resolveSibling("${file.nameWithoutExtension}.$it")
        }.filter { it.exists() }.map {
            // It doesn't really make sense to have >1 subtitle file so we don't need to batch the queries.
            getIdForPath(context, it)
        }.filter { it != null }
            .map { ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), it!!) })
        return delete(context, uris)
    }

    suspend fun deletePlaylist(context: MainActivity, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
        )
        return delete(context, setOf(uri))
    }

    // requires requestLegacyExternalStorage for simplicity
    private suspend fun delete(context: MainActivity, uris: Set<Uri>): (() -> Unit)? {
        if (needRequestWrite(context, uris)) {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver, uris.toList()
            )
            val req = Bundle().apply {
                putString("UiError", context.getString(
                    androidx.media3.session.R.string.error_message_info_cancelled))
            }
            withContext(Dispatchers.Main) {
                context.runIntentForDelete(pendingIntent.intentSender, req)
            }
            return null
        } else if (hasScopedStorageV1()) { // since Q, this actually deletes file and not just row
            return {
                CoroutineScope(Dispatchers.IO).launch {
                    val urisWithStatus = uris.map {
                        try {
                            it to (context.contentResolver.delete(it, null, null) == 1)
                        } catch (e: SecurityException) {
                            Log.e("ItemManipulator", "failed to delete $it", e)
                            it to e
                        }
                    }
                    val notOk = urisWithStatus.filter { it.second != true }
                    val ok = notOk.isEmpty()
                    if (!ok && hasScopedStorageV2()) {
                        val pendingIntent = MediaStore.createDeleteRequest(
                            context.contentResolver, notOk.map { it.first }
                        )
                        val req = Bundle().apply {
                            putString("UiError", context.getString(
                                androidx.media3.session.R.string.error_message_info_cancelled))
                        }
                        withContext(Dispatchers.Main) {
                            context.runIntentForDelete(pendingIntent.intentSender, req)
                        }
                    } else if (!ok) { // TODO(ASAP): recoverable security exception
                        val firstError = notOk.find { it.second is Throwable }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                context.getString(R.string.delete_failed,
                                    firstError?.toString() ?: context.getString(
                                        androidx.media3.session.R.string.error_message_info_cancelled)),
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            return { //TODO(ASAP) is this good ux
                CoroutineScope(Dispatchers.IO).launch {
                    val failures = mutableListOf<Throwable>()
                    val volumes = StorageManagerCompat(context).storageVolumes
                    val urisToDelete = ArrayList(uris.mapNotNull {
                        val path = try {
                            getPathForId(context, it)
                        } catch (t: Throwable) {
                            failures.add(t)
                            return@mapNotNull null
                        }
                        val volume = getVolumeForPath(volumes, path)
                        if (volume == null) {
                            failures.add(IOException("no volume for $path"))
                            return@mapNotNull null
                        }
                        if (volume.directory == null) {
                            failures.add(IOException("no mount for ${volume.getDescription(context)}"))
                            return@mapNotNull null
                        }
                        if (volume.isEmulated) {
                            path.toUriCompat().toString()
                        } else {
                            DocumentsContract.buildDocumentUri(
                                "com.android.externalstorage.documents",
                                "${volume.uuid}:" +
                                        path.toRelativeString(volume.directory)
                            ).toString()
                        }
                    })
                    if (failures.isNotEmpty()) {
                        // Report those failures early and if anything is left, try to operate on
                        // these remaining URIs.
                        for (t in failures) {
                            Log.e(TAG, "failed to delete file", t)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                context.getString(R.string.delete_failed,
                                    failures.first()),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    val pendingVolumes = pendingVolumesFromUris(context, urisToDelete, null)
                    if (!createAndLaunchSafPickIntent(
                            context, urisToDelete, pendingVolumes,
                            true
                        )
                    )
                        deleteSafFileMixedAssumePerm(context, urisToDelete)
                }
            }
        }
    }

    private fun getVolumeForRootId(volumes: List<StorageManagerCompat.StorageVolumeCompat>,
                                   rootId: String): StorageManagerCompat.StorageVolumeCompat? {
        return if (rootId == "primary") volumes.find { it.isEmulated }
        else volumes.find { it.uuid == rootId }
    }

    private fun getVolumeForPath(volumes: List<StorageManagerCompat.StorageVolumeCompat>,
                                 path: File): StorageManagerCompat.StorageVolumeCompat? {
        return volumes.find {
            it.directory?.let {
                var dirPath = it.absolutePath
                val filePath = path.absolutePath
                if (dirPath == filePath) {
                    return@let true
                }
                if (!dirPath.endsWith("/")) {
                    dirPath += "/"
                }
                return@let filePath.startsWith(dirPath)
            } == true
        }
    }

    private suspend fun createAndLaunchSafPickIntent(context: MainActivity,
                                                    uris: ArrayList<String>,
                                                    pendingVolumes: ArrayList<String>,
                                                    allowYesNo: Boolean = true): Boolean {
        if (pendingVolumes.isEmpty()) {
            return false
        }
        val volumes = StorageManagerCompat(context).storageVolumes
            .filter { !it.isEmulated }
        var volume: StorageManagerCompat.StorageVolumeCompat?
        do {
            val nextVolume = pendingVolumes.removeAt(0)
            // If a volume suddenly disappeared, we skip it and still try to request
            // the others (if any). We can't do much more about it anyway.
            volume = volumes.find { it.uuid == nextVolume }
        } while (volume == null && pendingVolumes.isNotEmpty())
        if (volume == null) {
            return false
        }
        val req = Bundle()
        req.putStringArrayList("UrisToDelete", uris)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && allowYesNo
        ) {
            req.putBoolean("IsMiniSafYesNo", true)
            val intent = volume.createAccessIntent(null)!!
            withContext(Dispatchers.Main) {
                Toast.makeText(context,context.getString(R.string.please_allow_to_delete), Toast.LENGTH_LONG).show()
                context.runIntentForDelete(intent, req)
            }
        } else {
            req.putBoolean("IsMiniSafYesNo", false)
            val intent = volume.createOpenDocumentTreeIntent()
            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.please_allow_to_delete),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.choose_sd,
                            volume.getDescription(context)),
                        Toast.LENGTH_LONG
                    ).show()
                }
                context.runIntentForDelete(intent, req)
            }
        }
        return true
    }

    // only used on R and later
    suspend fun continueDeleteFromPendingIntent(context: Context, resultCode: Int, req: Bundle) {
        // this is the callback of createDeleteRequest(), and the delete was already done if
        // resultCode is RESULT_OK. if it's not, then we just show a toast or something.
        if (resultCode == Activity.RESULT_OK) return
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.delete_failed,
                req.getString("UiError")), Toast.LENGTH_LONG).show()
        }
    }

    // only used before R
    suspend fun continueDeleteFromIntent(context: MainActivity, resultCode: Int, data: Intent?, req: Bundle) {
        // all URIs of files which are on SD card are SAF document URIs (not tree-document),
        // all others are file://
        val urisToDelete = req.getStringArrayList("UrisToDelete")!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            req.getBoolean("IsMiniSafYesNo")) {
            val pendingVolumes = pendingVolumesFromUris(context, urisToDelete, data?.data)
            if (resultCode == Activity.RESULT_OK) {
                val newUri = data!!.data!!
                Log.i(TAG, "got access to $newUri")
                // Let's persist this so that we don't have to ask again next time.
                maybeTakePersistableUriPermission(
                    context,
                    newUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                if (pendingVolumes.isNotEmpty()) {
                    // if the user granted permission, it may still not be enough to proceed to
                    // deletion: we can ask for at most 1 volume per dialog, so we need to show
                    // another one if there's multiple SD cards that are relevant to deletion.
                    if (createAndLaunchSafPickIntent(context, urisToDelete, pendingVolumes,
                        true))
                        return
                    // So we did originally want to request another volume, but now we can't
                    // find it. Try to delete the files, it'll probably fail but we'll get
                    // an A for effort.
                }
            } else {
                // this is a yes-no dialog box which the user can permanently dismiss for a given
                // folder ("don't ask again"). however it's not possible for the user to undo this
                // decision, so we need to fallback to another API if we get canceled result as user
                // may have accidentally perma-rejected access for this folder and would be stuck
                // with broken deletion if so. so we ask using the classic SAF folder picker (but we
                // should switch back to yes-no for other volumes for better UX).
                if (createAndLaunchSafPickIntent(context, urisToDelete, pendingVolumes,
                    false))
                    return
                // So we did originally want to request this same volume again, but now we can't
                // find it anymore. Try to delete the files, it'll probably fail but we'll get an A
                // for effort.
            }
            deleteSafFileMixedAssumePerm(context, urisToDelete)
        } else if (resultCode == Activity.RESULT_OK) { // ignore non-OK for classic picker, user may have changed his mind
            // this is the classic SAF folder picker, which may end up in selecting:
            // 1. a folder high enough to fulfill all delete requests (if all uris are on same
            //    volume)
            // 2. a folder too low to fulfill all of these, but useful for part of them (will
            //    always happen if the uris are not all on the same volume, but we need to ask
            //    for two or more volumes)
            // 3. something completely irrelevant (which we may or may not want to persist anyway)
            val newUri = data!!.data!!
            Log.i(TAG, "got access to $newUri")
            val tree = DocumentsContract.getTreeDocumentId(newUri)
            val volumeId = tree.split(':')[0]
            val pendingVolumes = pendingVolumesFromUris(context, urisToDelete, newUri)
            val isHelpful = pendingVolumes.isEmpty() || (volumeId != "primary" && urisToDelete.find {
                val uri = it.toUri()
                uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents"
                        && uri.pathSegments.let { ps ->
                            ps.size == 2 && ps[0] == "document" && ps[1].startsWith(tree)
                        }
            } != null)
            // TODO(ASAP): test if tree == "$volumeId:" works to check if this is root of SD
            if (isHelpful || (volumeId != "primary" && tree == "$volumeId:"
                        && isPublicVolume(context, volumeId))) {
                // if it's helpful to this deletion, let's take it. if it's the root of a SD card
                // that's not helpful, take it anyway because it's an opportunity to not have to ask
                // again when the user deletes something else (don't if it's not the root as we have
                // limited slots for persistable uris).
                maybeTakePersistableUriPermission(
                    context,
                    newUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            if (pendingVolumes.isNotEmpty()) {
                // either user did grant something, but the wrong thing and we send them back with a
                // reminder, or it was the right thing but we need to select more than one SD card.
                if (createAndLaunchSafPickIntent(context, urisToDelete, pendingVolumes,
                    false))
                    return
            }
            deleteSafFileMixedAssumePerm(context, urisToDelete)
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context,
                    context.getString(R.string.delete_failed,
                        context.getString(
                            androidx.media3.session.R.string.error_message_info_cancelled)),
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pendingVolumesFromUris(context: Context, urisToDelete: List<String>, extraGrant: Uri?): ArrayList<String> {
        if (urisToDelete.isEmpty()) return arrayListOf()
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
            .filter { it.uri.authority == "com.android.externalstorage.documents"
                    && DocumentsContractCompat.isTreeUri(it.uri)
                    && it.isReadPermission && it.isWritePermission }
            .map { DocumentsContract.getTreeDocumentId(it.uri) }
            .toMutableList()
        if (extraGrant != null && extraGrant.authority == "com.android.externalstorage.documents"
            && DocumentsContractCompat.isTreeUri(extraGrant))
            persistedUriPermissions.add(DocumentsContract.getTreeDocumentId(extraGrant))
        return ArrayList(urisToDelete.map { it.toUri() }.filter { uri ->
            uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents"
                    && uri.pathSegments.let { ps ->
                ps.size == 2 && ps[0] == "document" &&
                        persistedUriPermissions.find { prefix -> ps[1].startsWith(prefix) } == null
            }
        }.map { DocumentsContract.getDocumentId(it).split(':')[0] }
            .toSet() /* remove duplicates */)
    }

    private fun isPublicVolume(context: Context, uuid: String): Boolean {
        // Private volumes (USB OTG stick, non-adoptable SD card) aren't in this list, but could be
        // a volume id returned by ExternalStorageProvider
        return StorageManagerCompat(context).storageVolumes
            .find { !it.isEmulated && it.uuid == uuid } != null
    }

    // we only have limited (128-512) slots for persisted uri permissions, so try to be efficient
    private fun maybeTakePersistableUriPermission(context: Context, uri: Uri, flags: Int) {
        if (uri.authority != "com.android.externalstorage.documents"
            || !DocumentsContractCompat.isTreeUri(uri))
            throw IllegalArgumentException("not tree uri: $uri")
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val persistedUriPermissionsUnfiltered = context.contentResolver.persistedUriPermissions
        val persistedUriPermissions = persistedUriPermissionsUnfiltered
            .filter { it.uri.authority == "com.android.externalstorage.documents"
                    && DocumentsContractCompat.isTreeUri(it.uri) }
        val competingUris = persistedUriPermissions
            .filter { (it.isWritePermission ||
                    (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0)
                    && (it.isReadPermission ||
                    (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) }
            .map { DocumentsContract.getTreeDocumentId(it.uri) }
        if (competingUris.find { treeId.startsWith(it) } != null)
            return // we have a parent uri with enough permission already, we don't need this at all
        val maxSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 512 else 128
        if (persistedUriPermissionsUnfiltered.size == maxSize && // we need to get rid of some.
            persistedUriPermissionsUnfiltered.find { it.uri == uri } == null) {
            val treeContribution = persistedUriPermissions.size
            val nonTreeContribution = persistedUriPermissionsUnfiltered.size - treeContribution
            if (nonTreeContribution != 0) {
                // if we do this, we need to add some smart algo here to deal with out-of-space
                throw IllegalStateException("currently no other part of app calls " +
                        "takePersistableUriPermission, so why is there non-tree contribution?")
            }
            // if we ever persist tree URIs referenced in settings (for example allowing SAF media
            // scanner source), we should whitelist them to never release them here.
            val treesNonRoot = persistedUriPermissions.filter {
                DocumentsContract.getTreeDocumentId(it.uri)
                    .split(':')[1].isNotEmpty() }
            safelyReleasePersistablePermission(context, if (treesNonRoot.isNotEmpty()) {
                treesNonRoot.minBy { it.persistedTime }
            } else {
                persistedUriPermissions.minBy { it.persistedTime }
            })
        }
        context.contentResolver.takePersistableUriPermission(uri, flags)
        // If we got a parent URI with enough rights, we can get rid of all children grants.
        persistedUriPermissions.filter {
            DocumentsContract.getTreeDocumentId(it.uri).startsWith(treeId)
                    && (!it.isWritePermission ||
                    (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)
                    && (!it.isReadPermission ||
                    (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        }.forEach { safelyReleasePersistablePermission(context, it) }
    }

    private fun safelyReleasePersistablePermission(context: Context, it: UriPermission) {
        // Caution: if we call releasePersistableUriPermission after reboot, it will instantly
        // take our permission away. This could sabotage other threads that did not yet notice
        // that we got access to the parent and still try to use URIs with the child tree. To
        // avoid complex locking, just give ourselves an in-memory grant to the old child tree
        // (that will last until next reboot, after which we won't be trying to use the old
        // child tree anymore because it won't be in persistedUriPermissions list and all old
        // operations have completed one way or another with process death due to reboot).
        context.grantUriPermission(
            context.packageName,
            it.uri,
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    or (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0)
                    or (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        )
        context.contentResolver.releasePersistableUriPermission(it.uri,
            (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0)
                    or (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
    }

    private suspend fun deleteSafFileMixedAssumePerm(context: Context, urisToDelete: List<String>) {
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
            .filter { it.uri.authority == "com.android.externalstorage.documents"
                    && DocumentsContractCompat.isTreeUri(it.uri)
                    && it.isReadPermission && it.isWritePermission }
            .map { DocumentsContract.getTreeDocumentId(it.uri) }
        val volumes = StorageManagerCompat(context).storageVolumes
        val failed = urisToDelete.flatMap {
            val uri = it.toUri()
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val file = uri.toFile()
                val id = if (!hasScopedStorageV1()) getIdForPath(context, file) else null
                if (file.delete()) {
                    if (id != null && !hasScopedStorageV1())
                        context.contentResolver.delete(
                            MediaStore.Files.getContentUri("external"),
                            "${MediaStore.MediaColumns._ID} = ?",
                            arrayOf(id.toString())
                        ) // notify MediaStore if needed
                    emptyList()
                } else
                    listOf(null)
            } else {
                try {
                    val id = if (!hasScopedStorageV1())
                        getPathFromSafUri(volumes, uri)?.let { file -> getIdForPath(context, file) }
                    else null
                    val documentId = DocumentsContract.getDocumentId(uri)
                    val treeUri = persistedUriPermissions
                        .find { prefix -> documentId.startsWith(prefix) }?.let { treeId ->
                            DocumentsContract.buildTreeDocumentUri(
                                uri.authority, treeId
                            )
                        }
                    if (treeUri != null && DocumentsContract.deleteDocument(
                            context.contentResolver,
                            DocumentsContract.buildDocumentUriUsingTree(
                                treeUri, documentId
                            ))) {
                        if (id != null && !hasScopedStorageV1())
                            context.contentResolver.delete(
                                MediaStore.Files.getContentUri("external"),
                                "${MediaStore.MediaColumns._ID} = ?",
                                arrayOf(id.toString())
                            ) // notify MediaStore if needed
                        emptyList()
                    } else {
                        Log.w(TAG, "failed to delete (ret=false): $treeUri/document/$documentId")
                        listOf(null)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "failed to delete: $it", t)
                    listOf(t)
                }
            }
        }
        if (failed.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context,
                    context.getString(R.string.delete_failed,
                        failed.first().toString()),
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getPathFromSafUri(volumes: List<StorageManagerCompat.StorageVolumeCompat>,
                                  uri: Uri): File? {
        if (uri.authority != "com.android.externalstorage.documents")
            throw IllegalArgumentException("$uri")
        val documentId = DocumentsContract.getDocumentId(uri)
        val root = documentId.split(':')[0]
        val volume = getVolumeForRootId(volumes, root) ?: return null
        return volume.directory?.resolve(documentId.substring(root.length + 1))
    }

    fun setFavorite(context: Context, uris: Set<Uri>, favorite: Boolean): IntentSender? {
        if (!hasImprovedMediaStore()) {
            // TODO(ASAP) Q- support
            return null
        }
        if (hasMarkIsFavouriteStatus()) {
            MediaStore.markIsFavoriteStatus(
                context.contentResolver, uris.toList(), favorite
            )
            return null
        } else if (needRequestWrite(context, uris)) {
            // This never actually asks the user for permission...
            val pendingIntent = MediaStore.createFavoriteRequest(
                context.contentResolver, uris.toList(), favorite
            )
            return pendingIntent.intentSender
        } else {
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
            uris.forEach { uri ->
                if (context.contentResolver.update(uri, cv, null, null) != 1)
                    Log.w(TAG, "failed to favorite $uri")
            }
            return null
        }
    }

    fun createPlaylist(context: Context, name: String): File {
        val parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val out = File(parent, "$name.m3u")
        if (out.exists())
            throw IllegalArgumentException("tried to create playlist $out that already exists")
        PlaylistSerializer.write(context.applicationContext, out, listOf())
        return out
    }

    private fun getPathForId(context: Context, uri: Uri): File {
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri(uri.pathSegments.first()),
            arrayOf(MediaStore.MediaColumns.DATA),
            "${MediaStore.MediaColumns._ID} = ?",
            arrayOf(uri.pathSegments.last().toLong().toString()),
            null
        )
        if (cursor == null) throw NullPointerException("cursor is null")
        cursor.use {
            if (!cursor.moveToFirst()) throw NullPointerException("cursor is empty")
            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            return cursor.getString(dataColumn)?.let { File(it) }
                ?: throw NullPointerException("data is null")
        }
    }

    private fun getIdForPath(context: Context, file: File): Long? {
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            if (hasImprovedMediaStore())
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            else arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(file.absolutePath), null
        )
        if (cursor == null) return null
        cursor.use {
            if (!cursor.moveToFirst()) return null
            if (hasImprovedMediaStore()) {
                val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val type = cursor.getIntOrNullIfThrow(typeColumn)
                if (type != MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE) {
                    Log.e(TAG, "expected $file to be a subtitle")
                    return null
                }
            }
            val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            return cursor.getLongOrNullIfThrow(idColumn)
        }
    }

    @ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
    fun needRequestWrite(context: Context, uris: Set<Uri>): Boolean {
        for (uri in uris)
            if (needRequestWrite(context, uri))
                return true
        return false
    }

    @ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
    fun needRequestWrite(context: Context, uri: Uri): Boolean {
        return hasScopedStorageV2() && !checkIfFileAttributedToSelf(context, uri) &&
                context.checkUriPermission(
                    uri, Process.myPid(), Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ) != PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkIfFileAttributedToSelf(context: Context, uri: Uri): Boolean {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null
        )
        if (cursor == null) return false
        cursor.use {
            if (!cursor.moveToFirst()) return false
            val column = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            val pkg = cursor.getStringOrNullIfThrow(column)
            return pkg == context.packageName
        }
    }

    fun addToPlaylist(context: Context, out: File, songs: List<File>) {
        if (!out.exists())
            throw IllegalArgumentException("tried to change playlist $out that doesn't exist")
        setPlaylistContent(context, out, PlaylistSerializer.read(out) + songs)
    }

    fun setPlaylistContent(context: Context, out: File, songs: List<File>) {
        if (!out.exists())
            throw IllegalArgumentException("tried to change playlist $out that doesn't exist")
        val backup = out.readBytes()
        try {
            PlaylistSerializer.write(context.applicationContext, out, songs)
        } catch (t: Throwable) {
            try {
                PlaylistSerializer.write(
                    context.applicationContext, out.resolveSibling(
                        "${out.nameWithoutExtension}_NEW_${System.currentTimeMillis()}.m3u"
                    ), songs
                )
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!)
            }
            try {
                out.resolveSibling("${out.nameWithoutExtension}_BAK_${System.currentTimeMillis()}.${out.extension}")
                    .writeBytes(backup)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!)
            }
            throw t
        }
    }

    fun renamePlaylist(context: Context, out: File, newName: String) {
        val new = out.resolveSibling("$newName.${out.extension}")
        if (new.exists())
            throw IOException("can't rename to existing")
        // don't use normal rename methods as media store caches the old title in that case
        new.writeBytes(out.readBytes())
        if (!out.delete()) {
            MediaScannerConnection.scanFile(context, arrayOf(new.toString()), null) { path, uri ->
                if (uri == null && path == new.toString()) {
                    Log.e(TAG, "failed to scan renamed playlist $path")
                }
            }
            if (!hasScopedStorageV2())
                throw IOException("deletion of old file failed, both old and new files exist")
            throw DeleteFailedPleaseTryDeleteRequestException(
                MediaStore.createDeleteRequest(
                    context.contentResolver, listOf(new.toUriCompat())
                )
            )
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(out.toString(), new.toString()),
            null
        ) { path, uri ->
            if (uri == null && path == new.toString()) {
                Log.e(TAG, "failed to scan renamed playlist $path")
            }
        }
    }

    class DeleteFailedPleaseTryDeleteRequestException(val pendingIntent: PendingIntent) :
        Exception()
}