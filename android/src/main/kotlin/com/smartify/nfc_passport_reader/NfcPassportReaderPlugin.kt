package com.smartify.nfc_passport_reader

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.NewIntentListener
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import java.io.InputStream

/** NfcPassportReaderPlugin */
class NfcPassportReaderPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, NewIntentListener {
  private lateinit var channel : MethodChannel
  private var activity: Activity? = null
  private var pendingResult: Result? = null
  private var passportNumber: String? = null
  private var birthDate: String? = null
  private var expirationDate: String? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "nfc_passport_reader")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "startNFCReading" -> {
        passportNumber = call.argument("passportNumber")
        birthDate = call.argument("birthDate")
        expirationDate = call.argument("expirationDate")

        if (passportNumber.isNullOrEmpty() || birthDate.isNullOrEmpty() || expirationDate.isNullOrEmpty()) {
          return result.error("INVALID_ARGS", "Missing required parameters", null)
        }

        pendingResult = result
        startNFCReading()
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun startNFCReading() {
    activity?.let { activity ->
      val adapter = NfcAdapter.getDefaultAdapter(activity)
      when {
        adapter == null -> {
          pendingResult?.error(
            ERROR_NFC_NOT_AVAILABLE,
            "NFC is not available on this device",
            null
          )
          pendingResult = null
        }
        !adapter.isEnabled -> {
          pendingResult?.error(
            ERROR_NFC_DISABLED,
            "NFC is disabled. Please enable NFC in your device settings",
            null
          )
          pendingResult = null
        }
        else -> {
          val intent = Intent(activity, activity.javaClass)
          intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
          val pendingIntent = PendingIntent.getActivity(
            activity,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
          )
          val filter = arrayOf(arrayOf("android.nfc.tech.IsoDep"))
          adapter.enableForegroundDispatch(activity, pendingIntent, null, filter)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent): Boolean {
    if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
      val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
      }

      if (tag?.techList?.contains("android.nfc.tech.IsoDep") == true) {
        val convertedBirthDate = convertDate(birthDate)
        val convertedExpirationDate = convertDate(expirationDate)

        if (!passportNumber.isNullOrEmpty() && !convertedBirthDate.isNullOrEmpty() &&
          !convertedExpirationDate.isNullOrEmpty()) {
          try {
            val bacKey = BACKey(passportNumber!!, convertedBirthDate, convertedExpirationDate)
            readPassport(IsoDep.get(tag), bacKey)
            return true
          } catch (e: Exception) {
            reportError(ERROR_BAC_KEYS, "Invalid passport data provided")
            return false
          }
        } else {
          reportError(ERROR_INVALID_ARGS, "Missing or invalid passport data")
          return false
        }
      }
    }
    return false
  }

  private fun readPassport(isoDep: IsoDep, bacKey: BACKeySpec) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val result = NfcReader(activity!!).readPassport(isoDep, bacKey)
        when (result) {
          is NfcReader.PassportReadResult.Success -> {
            val mrzInfo = result.dg1File?.mrzInfo
            val dg11Data = result.dg11Data
            if (mrzInfo == null) {
              reportError(ERROR_READ_FAILED, "Failed to read MRZ info")
              return@launch
            }

            val imageBase64 = result.bitmap?.let { bitmap ->
              ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
              }
            }

            val passportData = hashMapOf<String, Any>(
              "firstName" to (mrzInfo.secondaryIdentifier.replace("<", " ")),
              "lastName" to (mrzInfo.primaryIdentifier.replace("<", " ")),
              "gender" to (mrzInfo.gender.toString()),
              "state" to (mrzInfo.issuingState),
              "nationality" to (mrzInfo.nationality),
              "passiveAuthSuccess" to result.passiveAuthSuccess,
              "chipAuthSuccess" to result.chipAuthSucceeded,
              "fullName" to (dg11Data?.fullName ?: ""),
              "otherNames" to (dg11Data?.otherNames ?: listOf<String>()),
              "personalNumber" to (dg11Data?.personalNumber ?: ""),
              "placeOfBirth" to (dg11Data?.placeOfBirth ?: ""),
              "residence" to (dg11Data?.residence ?: ""),
              "phoneNumber" to (dg11Data?.phoneNumber ?: ""),
              "profession" to (dg11Data?.profession ?: "")
            )
           result.imageBase64?.let {
              passportData["photo"] = it.filterNot {s -> s.isWhitespace() }
            }
            result.signatureImageBase64?.let {
              passportData["signature"] = it.filterNot {s -> s.isWhitespace() }
            }

            activity?.runOnUiThread {
              pendingResult?.success(passportData)
              pendingResult = null
            }
          }
          is NfcReader.PassportReadResult.Error -> {
            reportError(ERROR_READ_FAILED, result.exception.message ?: "Failed to read passport")
          }
        }
      } catch (e: Exception) {
        when (e) {
          is IOException -> reportError(ERROR_TAG_LOST, "Lost connection to passport. Please try again.")
          else -> reportError(ERROR_READ_FAILED, e.message ?: "Unknown error occurred")
        }
      }
    }
  }

  private fun reportError(code: String, message: String) {
    activity?.runOnUiThread {
      pendingResult?.error(code, message, null)
      pendingResult = null
    }
  }

  private fun convertDate(input: String?): String? {
    if (input == null) return null
    return try {
      SimpleDateFormat("yyMMdd", Locale.US).format(
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(input)!!
      )
    } catch (e: ParseException) {
      Log.w("PassportReaderPlugin", e)
      null
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addOnNewIntentListener(this)
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addOnNewIntentListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  companion object {
    private const val ERROR_NFC_NOT_AVAILABLE = "NFC_NOT_AVAILABLE"
    private const val ERROR_NFC_DISABLED = "NFC_DISABLED"
    private const val ERROR_INVALID_ARGS = "INVALID_ARGS"
    private const val ERROR_READ_FAILED = "READ_FAILED"
    private const val ERROR_BAC_KEYS = "BAC_KEYS_ERROR"
    private const val ERROR_TAG_LOST = "TAG_LOST"
  }

  }
