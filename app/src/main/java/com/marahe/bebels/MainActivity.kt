package com.marahe.bebels
import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.Constants.MessageNotificationKeys.TAG
import com.google.firebase.messaging.FirebaseMessaging

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    companion object {
        private const val BASE_URL = "https://bebels.bubbleapps.io/version-test/app"
        private const val FILE_CHOOSER_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION_REQUEST_CODE = 3
        private const val MIME_TYPE_IMAGE = "image/jpeg"
        var userID: String? = null
        var FCMtoken: String? = null
    }
    private var isSendToBubbleApiCalledSuccessfully = false
    lateinit var myWebView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraCaptureUri: Uri? = null
    private val headers = HashMap<String, String>()

    private fun requestCameraPermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        getToken()
        setTheme(R.style.Theme_Bebels)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        myWebView = findViewById(R.id.webView)
        findViewById<WebView>(R.id.webView).apply {
            webChromeClient = object : WebChromeClient() {



                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    // Esta línea imprimirá el mensaje de consola en tu consola de Android Studio
                    Log.d("MyApp", "${consoleMessage?.message()} -- line: ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                    // Buscar mensajes que comiencen con "userid="
                    val message = consoleMessage?.message()
                    if (message != null && message.startsWith("userid=")) {
                        userID = message.substringAfter("userid=")
                        // Ahora, userId contiene el ID del usuario
                        Log.d("MyApp", "User ID captured: $userID")
                        checkAndCallApi()
                    }
                    if (message != null && message.startsWith("vibración=")) {
                        val vibrationTime = message.substringAfter("vibración=").toLongOrNull()
                        if (vibrationTime != null) {
                            // Ahora, vibrationTime contiene el tiempo de vibración en milisegundos
                            Log.d("MyApp", "Tiempo de vibración capturado: $vibrationTime")
                            // Vibrate phone here
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (vibrator.hasVibrator()) {
                                vibrator.vibrate(vibrationTime)
                            }
                        }
                    }
                    return true
                }
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    this@MainActivity.filePathCallback = filePathCallback
                    val intentGallery = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                    val intentCamera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        requestCameraPermission()
                        cameraCaptureUri = createImageUri()
                        putExtra(MediaStore.EXTRA_OUTPUT, cameraCaptureUri)
                    }
                    val chooserIntent =
                        Intent.createChooser(intentGallery, "Seleccione una imagen").apply {
                            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(intentCamera))
                        }
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE)
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let { injectJavaScript(it) }
                }


                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    try {
                        val url = request?.url.toString()
                        if (url.startsWith(BASE_URL)) {
                            val connection = URL(url).openConnection() as HttpURLConnection
                            connection.setRequestProperty("Authorization", headers["Authorization"])
                            connection.connect()

                            // Leer la respuesta como texto
                            val stream = connection.inputStream
                            val responseString = stream.bufferedReader().use { it.readText() }
                            stream.close()

                            // Crear una nueva respuesta con el contenido leído como texto
                            val contentType = connection.contentType
                            val encoding = connection.contentEncoding
                            val headers = HashMap<String, String>()
                            for (key in connection.headerFields.keys) {
                                val values = connection.headerFields[key]
                                if (values != null && key != null) {
                                    headers[key] = values.joinToString(";")
                                }
                            }
                            return WebResourceResponse(contentType, encoding, 200, "OK", headers, ByteArrayInputStream(responseString.toByteArray(Charsets.UTF_8)))
                        } else {
                            return super.shouldInterceptRequest(view, request)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return super.shouldInterceptRequest(view, request)

                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    view?.loadUrl(request?.url.toString())
                    return true


                }
            }


            settings.apply {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK // usar la caché si está disponible, sino cargar desde la red
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
            }
            // Agregar headers de autenticación
            headers["Authorization"] = "Basic " + Base64.encodeToString("username:password".toByteArray(), Base64.NO_WRAP)
            loadUrl(BASE_URL)
        }
    }

    private fun injectJavaScript(webView: WebView) {
        val script = """
        var meta = document.createElement('meta');
        meta.name = 'viewport';
        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
        var head = document.getElementsByTagName('head')[0];
        head.appendChild(meta);
    """
        webView.evaluateJavascript(script, null)
    }

    override fun onBackPressed() {
        Log.d("MyApp", "onBackPressed Called")
        if (myWebView.canGoBack()) {
            Log.d("MyApp", "WebView can go back")
            myWebView.goBack()
        } else {
            Log.d("MyApp", "WebView cannot go back")
            super.onBackPressed()
        }
    }

    private fun getToken(){
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            // Get new FCM registration token
            FCMtoken = task.result
            // Log
            println("token: ${FCMtoken}")
            checkAndCallApi()

        })
    }

    private fun sendToBubbleAPI(FCMtoken: String, userID: String) {
        val url = "https://soulmeet-app.bubbleapps.io/api/1.1/wf/fcmtoken&userid"
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val jsonObject = JSONObject()
        jsonObject.put("FCMtoken", userID)
        jsonObject.put("userID", FCMtoken)
        val requestBody = jsonObject.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Error sendToBubbleAPI: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                println("Received data sendToBubbleAPI:\n$responseBody")

                if (response.isSuccessful) {
                    // Suponiendo que el estado de "éxito" significa una respuesta exitosa.
                    isSendToBubbleApiCalledSuccessfully = true
                }
            }
        })
    }

    private fun checkAndCallApi() {
        if (userID != null && FCMtoken != null && !isSendToBubbleApiCalledSuccessfully) {
            println("checkAndCallApi USER AND TOKEN:\n$userID \n$FCMtoken")
            // Llamar a tu función API aquí
            sendToBubbleAPI(userID!!, FCMtoken!!)
        } else {
            println("NO ENVIADO. userID=${userID} FMCtoken=${FCMtoken}")
        }
    }

    private fun createImageUri(): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_IMAGE)
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                val result = if (data != null && resultCode == RESULT_OK) data.data else null
                if (result != null) {
                    val uriArray = arrayOf(result)
                    filePathCallback?.onReceiveValue(uriArray)
                } else if (cameraCaptureUri != null) {
                    // Si el resultado de la galería es nulo, usar la Uri de la cámara
                    val uriArray = arrayOf(cameraCaptureUri!!)
                    filePathCallback?.onReceiveValue(uriArray)
                } else {
                    filePathCallback?.onReceiveValue(null)
                }
                filePathCallback = null
            }
        }
    }

}
