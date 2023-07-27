package com.example.nsdfinal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.nsdfinal.R
import com.hbisoft.pickit.PickiT
import com.hbisoft.pickit.PickiTCallbacks
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.Socket
import java.util.ArrayList

class NsdChatActivity : AppCompatActivity(),PickiTCallbacks {
    var mNsdHelper: NsdHelper? = null
    private var mStatusView: TextView? = null
    private var mUpdateHandler: Handler? = null
    var mConnection: ChatConnection? = null
    private lateinit var pickiT: PickiT
    private val REQUEST_CODE = 200


    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Creating chat activity")
        setContentView(R.layout.activity_nsd_chat)
        mStatusView = findViewById<View>(R.id.status) as TextView
        mUpdateHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                val chatLine = msg.data.getString("msg")
                addChatLine(chatLine)
            }
        }

        pickiT = PickiT(this, this,this)

        if (permissionAlreadyGranted()) {
            Toast.makeText(this, "Permission is already granted!", Toast.LENGTH_SHORT).show()
        }

        requestPermission()
    }

    fun clickAdvertise(v: View?) {
        // Register service
        if (mConnection!!.mPort > -1) {
            mNsdHelper!!.registerService(mConnection!!.mPort)
        } else {
            Log.d(TAG, "ServerSocket isn't bound.")
        }
    }

    fun clickDiscover(v: View?) {
        mNsdHelper!!.discoverServices()
    }

    fun clickConnect(v: View?) {
        val service = mNsdHelper!!.chosenServiceInfo
        if (service != null) {
            Log.d(TAG, "Connecting.")
            mConnection!!.connectToServer(
                service.host,
                service.port
            )
        } else {
            Log.d(TAG, "No service to connect to!")
        }
    }

    fun clickSend(v: View?) {
        val messageView = findViewById<EditText>(R.id.chatInput)
        if (messageView != null) {
            val messageString = messageView.text.toString()
            if (!messageString.isEmpty()) {
                object : AsyncTask<Void?, Void?, Void?>() {
                    override fun doInBackground(vararg voids: Void?): Void? {
                        Log.d(TAG, "doInBackground: message $mConnection")
                        mConnection?.sendMessage(messageString)
                        return null
                    }

                    override fun onPostExecute(aVoid: Void?) {
                        messageView.setText("")
                    }
                }.execute()
            }
        }
    }

    fun clickAccesStorage(v:View){


        if(checkFilePermission()){
            val intent = Intent()
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "*/*"
            startActivityForResult(Intent.createChooser(intent, "Select file"), 1)
        }

      else{
            requestPermission()
      }
    }

    private fun checkFilePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun permissionAlreadyGranted(): Boolean {
        val result = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_CODE
        )
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        @NonNull permissions: Array<String>,
        @NonNull grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission is denied!", Toast.LENGTH_SHORT).show()
                val showRationale =
                    shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                if (!showRationale) {
                    //openSettingsDialog()
                }
            }
        }
    }

    fun addChatLine(line: String?) {
        mStatusView!!.append(
            """
    
    $line
    """.trimIndent()
        )
    }

    override fun onStart() {
        Log.d(TAG, "Starting.")
        mConnection = mUpdateHandler?.let { ChatConnection(it) }
        mNsdHelper = NsdHelper(this)
        mNsdHelper!!.initializeNsd()
        super.onStart()
    }

    override fun onPause() {
        Log.d(TAG, "Pausing.")
        if (mNsdHelper != null) {
            mNsdHelper!!.stopDiscovery()
        }
        super.onPause()
    }

    override fun onResume() {
        Log.d(TAG, "Resuming.")
        super.onResume()
        if (mNsdHelper != null) {
            mNsdHelper!!.discoverServices()
        }
    }


    override fun onStop() {
        Log.d(TAG, "Being stopped.")
        mNsdHelper!!.tearDown()
        mConnection!!.tearDown()
        mNsdHelper = null
        mConnection = null
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "Being destroyed.")
        super.onDestroy()
    }

    companion object {
        const val TAG = "NsdChat"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            pickiT.getPath(data?.data, Build.VERSION.SDK_INT)
        }
    }

    override fun PickiTonUriReturned() {
       Unit
    }

    override fun PickiTonStartListener() {
        Unit
    }

    override fun PickiTonProgressUpdate(progress: Int) {
        Unit
    }

    override fun PickiTonCompleteListener(
        path: String?,
        wasDriveFile: Boolean,
        wasUnknownProvider: Boolean,
        wasSuccessful: Boolean,
        Reason: String?
    ) {
        Log.d(TAG, "PickiTonCompleteListener: Directory was$path")
        if (path != null) {

            object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg voids: Void?): Void? {
                Log.d(TAG, "sendFile: before ")
                Log.d(TAG, "doInBackground: $mConnection")
                mConnection?.sendFile(path)
                return null
            }

            override fun onPostExecute(aVoid: Void?) {
               // messageView.setText("")
            }
        }.execute()
        }
    }

    override fun PickiTonMultipleCompleteListener(
        paths: ArrayList<String>?,
        wasSuccessful: Boolean,
        Reason: String?
    ) {
        Unit
    }


}