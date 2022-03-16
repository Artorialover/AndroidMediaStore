package com.zpc.stroagedemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import androidx.core.app.ActivityCompat
import com.zpc.asukalib.queryImages
import com.zpc.asukalib.saveImages
import com.zpc.stroagedemo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    lateinit var binding:ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        init()
    }

    fun init(){
        binding.btnReadSelf.setOnClickListener {
            GlobalScope.launch(context = Dispatchers.IO) {
                queryImages(this@MainActivity)
            }

        }
        binding.btnSave.setOnClickListener {
            GlobalScope.launch(context = Dispatchers.IO) {
                val bitmap=BitmapFactory.decodeResource(resources,R.drawable.t1)
                saveImages(this@MainActivity,bitmap)
            }
        }
    }


}

