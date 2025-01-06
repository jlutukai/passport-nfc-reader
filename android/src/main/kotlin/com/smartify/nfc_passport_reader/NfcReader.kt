package com.smartify.nfc_passport_reader

import android.content.Context
import android.graphics.Bitmap
import android.nfc.tech.IsoDep
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import org.apache.commons.io.IOUtils
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.ChipAuthenticationPublicKeyInfo
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.icao.DG14File
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.DG11File
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.ASN1StreamParser
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.DLApplicationSpecific
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.DLTaggedObject
import org.bouncycastle.asn1.x509.Certificate
import org.jmrtd.lds.icao.DG7File
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.*

class NfcReader constructor(private val  context: Context) {

    // Assuming we're still in the MainActivity class
     suspend fun readPassport(isoDep: IsoDep, bacKey: BACKeySpec) = withContext(Dispatchers.IO) {
        var dg1File: DG1File? = null
        var dg2File: DG2File? = null
        var dg14File: DG14File? = null
        var dg11File: DG11File? = null
        var sodFile: SODFile? = null
        var imageBase64: String? = null
        var bitmap: Bitmap? = null
        var chipAuthSucceeded = false
        var passiveAuthSuccess = false
        var dg14Encoded: ByteArray? = null
        var dg7File: DG7File? = null  // Add this
        var signatureImage: Bitmap? = null
        var signatureImageBase64: String? = null

        try {
            isoDep.timeout = 10000
            val cardService = CardService.getInstance(isoDep)
            cardService.open()
            val service = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                false,
                false,
            )
            service.open()

            var paceSucceeded = false
            try {
                val cardAccessFile = CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
                val securityInfoCollection = cardAccessFile.securityInfos
                for (securityInfo: SecurityInfo in securityInfoCollection) {
                    if (securityInfo is PACEInfo) {
                        service.doPACE(
                            bacKey,
                            securityInfo.objectIdentifier,
                            PACEInfo.toParameterSpec(securityInfo.parameterId),
                            null,
                        )
                        paceSucceeded = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, e)
            }

            service.sendSelectApplet(paceSucceeded)
            if (!paceSucceeded) {
                try {
                    service.getInputStream(PassportService.EF_COM).read()
                } catch (e: Exception) {
                    service.doBAC(bacKey)
                }
            }

            // Read DG1, DG2, and SOD files
            dg1File = DG1File(service.getInputStream(PassportService.EF_DG1))
            dg2File = DG2File(service.getInputStream(PassportService.EF_DG2))
            sodFile = SODFile(service.getInputStream(PassportService.EF_SOD))
            val dg11In = service.getInputStream(PassportService.EF_DG11)
            val dg11Encoded = IOUtils.toByteArray(dg11In)
            val dg11InputStream = ByteArrayInputStream(dg11Encoded)
            dg11File = DG11File(dg11InputStream)

            // Perform Chip Authentication
            chipAuthSucceeded = doChipAuth(service)

            // Perform Passive Authentication
            passiveAuthSuccess = doPassiveAuth(sodFile, dg1File, dg2File, dg14Encoded, chipAuthSucceeded)

            // Process face image
            val allFaceImageInfo = dg2File.faceInfos.flatMap { it.faceImageInfos }
            if (allFaceImageInfo.isNotEmpty()) {
                val faceImageInfo = allFaceImageInfo.first()
                val imageLength = faceImageInfo.imageLength
                val buffer = DataInputStream(faceImageInfo.imageInputStream).use { stream ->
                    ByteArray(imageLength).also { buffer ->
                        stream.readFully(buffer, 0, imageLength)
                    }
                }

                bitmap = ByteArrayInputStream(buffer, 0, imageLength).use { inputStream ->
                    ImageUtil.decodeImage(context, faceImageInfo.mimeType, inputStream)
                }
                imageBase64 = Base64.encodeToString(buffer, Base64.DEFAULT)
            }

            try {
                val dg7In = service.getInputStream(PassportService.EF_DG7)
                dg7File = DG7File(dg7In)
                // Get the signature image
                if (dg7File.images.isNotEmpty()) {
                    val signatureInfo = dg7File.images.first()
                    val imageLength = signatureInfo.imageLength
                    val buffer = DataInputStream(signatureInfo.imageInputStream).use { stream ->
                        ByteArray(imageLength).also { buffer ->
                            stream.readFully(buffer, 0, imageLength)
                        }
                    }

                    signatureImage = ByteArrayInputStream(buffer, 0, imageLength).use { inputStream ->
                        ImageUtil.decodeImage(context, signatureInfo.mimeType, inputStream)
                    }
                    signatureImageBase64 = Base64.encodeToString(buffer, Base64.DEFAULT)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read signature image: ${e.message}")
                // Continue without signature image
            }


            // Return success result
            PassportReadResult.Success(
                dg1File = dg1File,
                bitmap = bitmap,
                imageBase64 = imageBase64,
                passiveAuthSuccess = passiveAuthSuccess,
                chipAuthSucceeded = chipAuthSucceeded,
                dg11Data = dg11File.getData(),
                signatureImage = signatureImage,
                signatureImageBase64 = signatureImageBase64
            )
        } catch (e: Exception) {
            PassportReadResult.Error(e)
        }
    }

    private suspend fun doChipAuth(service: PassportService): Boolean = withContext(Dispatchers.IO) {
        try {
            val dg14In = service.getInputStream(PassportService.EF_DG14)
            val dg14Encoded = IOUtils.toByteArray(dg14In)
            val dg14File = DG14File(ByteArrayInputStream(dg14Encoded))

            for (securityInfo in dg14File.securityInfos) {
                if (securityInfo is ChipAuthenticationPublicKeyInfo) {
                    service.doEACCA(
                        securityInfo.keyId,
                        ChipAuthenticationPublicKeyInfo.ID_CA_ECDH_AES_CBC_CMAC_256,
                        securityInfo.objectIdentifier,
                        securityInfo.subjectPublicKey,
                    )
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, e)
            false
        }
    }

    private suspend fun doPassiveAuth(
        sodFile: SODFile?,
        dg1File: DG1File?,
        dg2File: DG2File?,
        dg14Encoded: ByteArray?,
        chipAuthSucceeded: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            sodFile?.let { sod ->
                val digest = MessageDigest.getInstance(sod.digestAlgorithm)
                val dataHashes = sod.dataGroupHashes

                val dg14Hash = if (chipAuthSucceeded && dg14Encoded != null) {
                    digest.digest(dg14Encoded)
                } else ByteArray(0)

                val dg1Hash = digest.digest(dg1File!!.encoded)
                val dg2Hash = digest.digest(dg2File!!.encoded)

                if (Arrays.equals(dg1Hash, dataHashes[1]) &&
                    Arrays.equals(dg2Hash, dataHashes[2]) &&
                    (!chipAuthSucceeded || Arrays.equals(dg14Hash, dataHashes[14]))) {

                    // Certificate validation logic
                    val asn1InputStream = ASN1InputStream(context.assets.open("masterList"))
                    val keystore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                        load(null, null)
                    }
                    val cf = CertificateFactory.getInstance("X.509")

                    var p: ASN1Primitive?
                    while (asn1InputStream.readObject().also { p = it } != null) {
                        val asn1 = ASN1Sequence.getInstance(p)
                        requireNotNull(asn1) { "Null sequence passed." }
                        require(asn1.size() == 2) { "Incorrect sequence size: ${asn1.size()}" }

                        val certSet = ASN1Set.getInstance(asn1.getObjectAt(1))
                        for (i in 0 until certSet.size()) {
                            val certificate = Certificate.getInstance(certSet.getObjectAt(i))
                            val pemCertificate = certificate.encoded
                            val javaCertificate = cf.generateCertificate(ByteArrayInputStream(pemCertificate))
                            keystore.setCertificateEntry(i.toString(), javaCertificate)
                        }
                    }

                    // Validate certificates
                    val docSigningCertificates = sod.docSigningCertificates
                    for (docSigningCertificate in docSigningCertificates) {
                        docSigningCertificate.checkValidity()
                    }

                    val cp = cf.generateCertPath(docSigningCertificates)
                    val pkixParameters = PKIXParameters(keystore).apply {
                        isRevocationEnabled = false
                    }

                    CertPathValidator.getInstance(CertPathValidator.getDefaultType())
                        .validate(cp, pkixParameters)

                    var sodDigestEncryptionAlgorithm = sod.docSigningCertificate.sigAlgName
                    val isSSA = sodDigestEncryptionAlgorithm == "SSAwithRSA/PSS"
                    if (isSSA) {
                        sodDigestEncryptionAlgorithm = "SHA256withRSA/PSS"
                    }

                    val sign = Signature.getInstance(sodDigestEncryptionAlgorithm).apply {
                        if (isSSA) {
                            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
                        }
                        initVerify(sod.docSigningCertificate)
                        update(sod.eContent)
                    }

                    return@withContext sign.verify(sod.encryptedDigest)
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, e)
            false
        }
    }

    // Result sealed class to handle success/error cases
    sealed class PassportReadResult {
        data class Success(
            val dg1File: DG1File?,
            val bitmap: Bitmap?,
            val imageBase64: String?,
            val passiveAuthSuccess: Boolean,
            val chipAuthSucceeded: Boolean,
            val dg11Data: DG11Data?,
            val signatureImage: Bitmap?,
            val signatureImageBase64: String?
        ) : PassportReadResult()

        data class Error(val exception: Exception) : PassportReadResult()
    }



    companion object{
        const val TAG = "reading_tag"
    }
}

data class DG11Data(
    val fullName: String?,
    val otherNames: List<String>,
    val personalNumber: String?,
    val placeOfBirth: String?,
    val residence: String?,
    val phoneNumber: String?,
    val profession: String?
)

    fun DG11File.getData(): DG11Data = DG11Data(
        fullName = nameOfHolder.toString(),
        otherNames = otherNames,
        personalNumber = personalNumber,
        placeOfBirth = placeOfBirth.joinToString(),
        residence =  permanentAddress.joinToString(),
        phoneNumber = telephone,
        profession = profession
    )
