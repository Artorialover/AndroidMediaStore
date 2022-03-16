package com.zpc.asukalib

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startIntentSenderForResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


/**
 * ref: https://mp.weixin.qq.com/s/l6tk2xDCWBdZgQmeOCbnfg
 */

/**
 * Android Q以下，如果不申请READ_EXTERNAL_STORAGE权限，会报错；申请后，会读到所有的图片。DATA字段包含了绝对路径
 * Android Q以上，如果不申请READ_EXTERNAL_STORAGE权限，只能读到App自己的图片;申请后，会读到所有的图片。Relative_Path字段包含相关信息
 */
fun queryImages(activity: Activity){
    val permission=Manifest.permission.READ_EXTERNAL_STORAGE;
    if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q){
        //检查权限
        if(ActivityCompat.checkSelfPermission(activity,permission)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(activity, arrayOf(permission),10086)
            return
        }
    }
//    if(ActivityCompat.checkSelfPermission(activity,permission)!=PackageManager.PERMISSION_GRANTED){
//        ActivityCompat.requestPermissions(activity, arrayOf(permission),10086)
//        return
//    }
    queryAllImages(activity)
}

private fun queryAllImages(context: Context):ArrayList<Uri>{
    val externalContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    var projection:Array<String>?=null

    var appendId:Uri.Builder?=null;
    projection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH)
    }else{
        arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.DATA)
    }
    val cursor = context.contentResolver.query(externalContentUri, projection, null, null, null)

    val uris = ArrayList<Uri>()
    cursor?.let {
        if(cursor.moveToFirst()){
            do {
                appendId = ContentUris.appendId(externalContentUri.buildUpon(), cursor.getLong(0))
                appendId?.let {
                    uris.add(it.build())
                    Log.d("SMG",it.build().toString())
                }
                val string1 = cursor.getString(1)
                Log.d("SMG",string1)
                val string2 = cursor.getString(2)
                Log.d("SMG",string2)
            }while (cursor.moveToNext())
        }
        cursor.close()
    }
    return uris
}

/**
 * Android Q以下需要申请WRITE_EXTERNAL权限，直接使用File的Api保存文件并通知系统扫描媒体数据库
 * Android Q及以上版本不需要申请WRITE_EXTERNAL,使用MediaStore方式存储。
 */
fun saveImages(activity: Activity,bitmap: Bitmap){
    val permission=Manifest.permission.WRITE_EXTERNAL_STORAGE
    if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q){
        //检查权限
        if(ActivityCompat.checkSelfPermission(activity,permission)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(activity, arrayOf(permission),10086)
            return
        }
    }
    saveMedia(activity,{
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
    },Environment.DIRECTORY_PICTURES,"ASUKA","1.png","image/png")
}

//type:Environment.DIRECTORY_PICTURES
private fun saveMedia(context: Context, streamOperation:(OutputStream)->Unit, dirType:String, relativeDir:String, filename:String, mimeType:String, description:String=""){
    if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q){
        //首先保存
        var saveDir =
            Environment.getExternalStoragePublicDirectory(dirType)
        saveDir=File(saveDir, relativeDir);
        if(!saveDir.exists() && !saveDir.mkdirs()){
            throw Exception("create directory fail!")
        }
        Log.d("SMG",saveDir.absolutePath)
        val outputFile=File(saveDir, filename)
        val fos = FileOutputStream(outputFile)
        streamOperation(fos)
        fos.flush()
        fos.close()
        //把文件插入到系统图库(直接插入到Picture文件夹下)
//        MediaStore.Images.Media.insertImage(
//            context.contentResolver, outputFile.absolutePath, outputFile.name, ""
//        )
        //最后通知图库更新
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outputFile)))
    }else{
        val path=if(relativeDir.isNotEmpty()) Environment.DIRECTORY_PICTURES+File.separator+relativeDir else Environment.DIRECTORY_PICTURES
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME,filename)
        contentValues.put(MediaStore.Images.Media.DESCRIPTION,description)
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH,path)
        contentValues.put(MediaStore.Images.Media.MIME_TYPE,mimeType)
        //contentValues.put(MediaStore.Images.Media.IS_PENDING,1)

        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val insertUri = context.contentResolver.insert(external, contentValues)

        var fos: OutputStream?=null
        try {
            insertUri?.let {
                fos=context.contentResolver.openOutputStream(it)
            }
            fos?.let {
                streamOperation(it)
            }
        }catch (e: IOException){
            Log.d("SMG","${e.message}")
        }finally {
            fos?.close()
        }
    }
}

/**
 * Android Q以下版本，删除文件需要申请WRITE_EXTERNAL_STORAGE权限。通过MediaStore的DATA字段获得媒体文件的绝对路径，然后使用File相关API删除
 *
 * Android Q以上版本，应用删除自己创建的媒体文件不需要用户授权。删除其他应用创建的媒体文件需要申请READ_EXTERNAL_STORAGE权限。
 * 删除其他应用创建的媒体文件，还会抛出RecoverableSecurityException异常，在操作或删除公共目录的文件时，需要Catch该异常，由MediaProvider弹出弹框给用户选择是否允许应用修改或删除图片/视频/音频文件
 */
fun deletePicture(activity: Activity, imageUri: Uri){
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        try {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = activity.contentResolver.query(imageUri, projection,
                null, null, null)
            cursor?.let{
                val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                if (columnIndex > -1) {
                    val file = File(it.getString(columnIndex))
                    file.delete()
                }
            }
            cursor?.close()
        } catch (e: IOException) {
            Log.e("SMG", "delete failed :${e.message}")
        }
    }else{
        try {
            activity.contentResolver.delete(imageUri,null,null)
        } catch (e: IOException) {
            Log.e("SMG", "delete failed :${e.message}")
        }catch (e1: RecoverableSecurityException){
            //捕获 RecoverableSecurityException异常，发起请求
            try {
                startIntentSenderForResult(activity,e1.userAction.actionIntent.intentSender,
                    10086, null, 0, 0, 0,null)
            } catch (e2: IntentSender.SendIntentException) {
                e2.printStackTrace()
            }
        }
    }
}

/**
 * 让MediaStore更新数据。否则如果直接把文件放在媒体文件夹中，可能扫描不到
 * ref: https://stackoverflow.com/questions/4646913/android-how-to-use-mediascannerconnection-scanfile
 * https://www.javaobj.com/2020/03/android-media-scan/
 *
 */
fun scan(context: Context){
    MediaScannerConnection.scanFile(context,arrayOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath()),
        arrayOf("video/mp4", "audio/mp3", "image/jpeg"),object :MediaScannerConnection.OnScanCompletedListener{
            override fun onScanCompleted(path: String?, uri: Uri?) {
                TODO("Not yet implemented")
            }
        })
}