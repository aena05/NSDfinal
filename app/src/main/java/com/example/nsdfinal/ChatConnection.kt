package com.example.nsdfinal

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class ChatConnection(private val mUpdateHandler: Handler) {
    private lateinit var  context : Context
    private val mChatServer: ChatServer
    private var mChatClient: ChatClient? = null
    private var mSocket: Socket? = null
    var mPort = -1
    var classPath: String = String()


    init {
        mChatServer = ChatServer(mUpdateHandler)
    }

    fun tearDown() {
        mChatServer.tearDown()
        if (mChatClient != null) {
            mChatClient!!.tearDown()
        }
    }

    fun connectToServer(address: InetAddress?, port: Int) {
        mChatClient = ChatClient(address, port)
    }

    fun sendMessage(msg: String) {
        if (mChatClient != null) {
            mChatClient!!.sendMessage(msg)
        }
    }

    fun sendFile(path: String){
        Log.d(TAG, "sendFile: inside ")
        if (mChatClient != null) {
            mChatClient!!.sendFile(path)
        }

    }

    @Synchronized
    fun updateMessages(msg: String, local: Boolean) {
        var msg = msg
        Log.e(TAG, "Updating message: $msg")
        msg = if (local) {
            "me: $msg"
        } else {
            "them: $msg"
        }
        val messageBundle = Bundle()
        messageBundle.putString("msg", msg)
        val message = Message()
        message.data = messageBundle
        mUpdateHandler.sendMessage(message)
    }

    // TODO(alexlucas): Auto-generated catch block
    @set:Synchronized
    private var socket: Socket?
        private get() = mSocket
        private set(socket) {
            Log.d(TAG, "setSocket being called.")
            if (socket == null) {
                Log.d(TAG, "Setting a null socket.")
            }
            if (mSocket != null) {
                if (mSocket!!.isConnected) {
                    try {
                        mSocket!!.close()
                    } catch (e: IOException) {
                        // TODO(alexlucas): Auto-generated catch block
                        e.printStackTrace()
                    }
                }
            }
            mSocket = socket
        }

    private inner class ChatServer(handler: Handler?) {
        var mServerSocket: ServerSocket? = null
        var mThread: Thread? = null

        init {
            mThread = Thread(ServerThread())
            mThread!!.start()
        }


        fun tearDown() {
            mThread!!.interrupt()
            try {
                mServerSocket!!.close()
            } catch (ioe: IOException) {
                Log.e(TAG, "Error when closing server socket.")
            }
        }

        internal inner class ServerThread : Runnable {


            override fun run() {
                try {
                    // Since discovery will happen via Nsd, we don't need to care which port is
                    // used.  Just grab an available one  and advertise it via Nsd.
                    mServerSocket = ServerSocket(0)
                    mPort = mServerSocket!!.localPort
                    while (!Thread.currentThread().isInterrupted) {
                        Log.d(TAG, "ServerSocket Created, awaiting connection")
                        socket = mServerSocket!!.accept()
                        Log.d(TAG, "Connected.")
                        if (mChatClient == null) {
                            val port = mSocket!!.port
                            val address = mSocket!!.inetAddress
                            connectToServer(address, port)
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error creating ServerSocket: ", e)
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class ChatClient(address: InetAddress?, port: Int) {
        private val mAddress: InetAddress?
        private val PORT: Int
        private val CLIENT_TAG = "ChatClient"
        private val mSendThread: Thread
        private var mRecThread: Thread? = null

        init {
            Log.d(CLIENT_TAG, "Creating chatClient")
            mAddress = address
            PORT = port
            mSendThread = Thread(SendingThread())
            mSendThread.start()
        }

        internal inner class SendingThread : Runnable {
            var mMessageQueue: BlockingQueue<String>
            private val QUEUE_CAPACITY = 10

            init {
                mMessageQueue = ArrayBlockingQueue(QUEUE_CAPACITY)
            }

            override fun run() {
                try {
                    if (socket == null) {
                        socket = Socket(mAddress, PORT)
                        Log.d(CLIENT_TAG, "Client-side socket initialized.")
                    } else {
                        Log.d(CLIENT_TAG, "Socket already initialized. skipping!")
                    }
                    mRecThread = Thread(ReceivingThread())
                    mRecThread!!.start()
                } catch (e: UnknownHostException) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, UHE", e)
                } catch (e: IOException) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, IOE.", e)
                }
                while (true) {
                    try {
                       val msg = mMessageQueue.take()
                        sendMessage(msg)
                    } catch (ie: InterruptedException) {
                        Log.d(CLIENT_TAG, "Message sending loop interrupted, exiting")
                    }
                }
            }
        }

        internal inner class ReceivingThread : Runnable {
            override fun run() {
                val input: BufferedReader
                try {
                    input = BufferedReader(
                        InputStreamReader(
                            mSocket!!.getInputStream()
                        )
                    )
                    while (!Thread.currentThread().isInterrupted) {
                        var messageStr: String? = null
                        messageStr = input.readLine()
                        if (messageStr != null) {
                            Log.d(CLIENT_TAG, "Read from the stream: $messageStr")
                            updateMessages(messageStr, false)
                        } else {
                            Log.d(CLIENT_TAG, "The nulls! The nulls!")
                            break
                        }
                    }
                    input.close()
                } catch (e: IOException) {
                    Log.e(CLIENT_TAG, "Server loop error: ", e)
                }
            }
        }

        fun tearDown() {
            try {
                socket?.close()
            } catch (ioe: IOException) {
                Log.e(CLIENT_TAG, "Error when closing server socket.")
            }
        }

        fun sendMessage(msg: String) {
            try {
                val socket: Socket? = socket
                if (socket == null) {
                    Log.d(CLIENT_TAG, "Socket is null, wtf?")
                } else if (socket.getOutputStream() == null) {
                    Log.d(CLIENT_TAG, "Socket output stream is null, wtf?")
                }
                val out = PrintWriter(
                    BufferedWriter(
                        OutputStreamWriter(socket?.getOutputStream())
                    ), true
                )
                out.println(msg)
                out.flush()
                updateMessages(msg, true)
            } catch (e: UnknownHostException) {
                Log.d(CLIENT_TAG, "Unknown Host", e)
            } catch (e: IOException) {
                Log.d(CLIENT_TAG, "I/O Exception", e)
            } catch (e: Exception) {
                Log.d(CLIENT_TAG, "Error3", e)
            }
            Log.d(CLIENT_TAG, "Client sent message: $msg")
        }


        fun sendFile(path: String) {
            try {
                classPath = path
                val socket: Socket? = socket
                if (path[0] != '/') {
                    //path = "/storage/emulated/0/$path"
                }
                Log.d(TAG, "doInBackground: Storage Here $path")
                val file = File(path)
                if (path.isEmpty()) {
                    val toast = Toast.makeText(context, "Path is empty", Toast.LENGTH_SHORT)
                    toast.show()
                }
                Log.d(TAG, "doInBackground: $path")

                val fileInputStream = FileInputStream(file)
                val fileSize = file.length()
                val byteArray = ByteArray(fileSize.toInt())
                val dataInputStream = DataInputStream(fileInputStream)
                dataInputStream.readFully(byteArray, 0, byteArray.size)
                val outputStream = socket?.getOutputStream()
                val dataOutputStream = DataOutputStream(outputStream)
                dataOutputStream.writeUTF(file.name)
                dataOutputStream.writeLong(byteArray.size.toLong())
                //filenameX = file.name
                dataOutputStream.write(byteArray, 0, byteArray.size)
                dataOutputStream.flush()
                if (outputStream != null) {
                    outputStream.write(byteArray, 0, byteArray.size)
                }
                if (outputStream != null) {
                    outputStream.flush()
                }
                if (outputStream != null) {
                    outputStream.close()
                }
                dataOutputStream.close()


            } catch (e: UnknownHostException) {
                Log.d(CLIENT_TAG, "Unknown Host", e)
            } catch (e: IOException) {
                Log.d(CLIENT_TAG, "I/O Exception", e)
            } catch (e: Exception) {
                Log.d(CLIENT_TAG, "Error3", e)
            }
            Log.d(CLIENT_TAG, "Client file path: $path")
        }


    }

    companion object {
        private const val TAG = "ChatConnection"
    }
}