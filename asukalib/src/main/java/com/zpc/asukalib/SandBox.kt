package com.zpc.asukalib

import android.content.Context
import android.os.Environment
import java.io.File
//沙盒目录没有限制,直接操作File相关的api即可

fun getPrivateFile(dirType:String="",context: Context,filename:String):File{
    val dir = context.getExternalFilesDir(dirType)
    return File(dir,filename)
}

