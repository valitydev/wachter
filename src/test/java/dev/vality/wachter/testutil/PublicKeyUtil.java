package dev.vality.wachter.testutil;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;

@UtilityClass
public class PublicKeyUtil {

    @SneakyThrows
    public String getModulus(PublicKey publicKey) {
        RSAPublicKey rsaPub  = (RSAPublicKey)(publicKey);
        BigInteger publicKeyModulus = rsaPub.getModulus();
        BigInteger publicKeyExponent  = rsaPub.getPublicExponent();
        System.out.println("publicKeyModulus: " + publicKeyModulus);
        System.out.println("publicKeyExponent: " + publicKeyExponent);
        String nModulus=Base64.getUrlEncoder().encodeToString(publicKeyModulus.toByteArray());
        String eExponent=Base64.getUrlEncoder().encodeToString(publicKeyExponent.toByteArray());
        System.out.println("n Modulus for RSA Algorithm: " + nModulus);
        System.out.println("e Exponent for RSA Algorithm: " + eExponent);
        return nModulus;
    }

    @SneakyThrows
    public String getExponent(PublicKey publicKey) {
        RSAPublicKey rsaPub  = (RSAPublicKey)(publicKey);
        BigInteger publicKeyModulus = rsaPub.getModulus();
        BigInteger publicKeyExponent  = rsaPub.getPublicExponent();
        System.out.println("publicKeyModulus: " + publicKeyModulus);
        System.out.println("publicKeyExponent: " + publicKeyExponent);
        String nModulus=Base64.getUrlEncoder().encodeToString(publicKeyModulus.toByteArray());
        String eExponent=Base64.getUrlEncoder().encodeToString(publicKeyExponent.toByteArray());
        System.out.println("n Modulus for RSA Algorithm: " + nModulus);
        System.out.println("e Exponent for RSA Algorithm: " + eExponent);
        return eExponent;
    }
}
