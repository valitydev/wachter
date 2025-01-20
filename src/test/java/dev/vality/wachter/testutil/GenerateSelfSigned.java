package dev.vality.wachter.testutil;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

public class GenerateSelfSigned {

    private static final Provider BC_PROVIDER = new BouncyCastleProvider();
    private static final SecureRandom PRNG        = new SecureRandom();

    public  static X509Certificate getCert(KeyPair keyPair) throws Exception {

        Security.insertProviderAt(BC_PROVIDER, 1);

        final var x500subject = getSubject();
        final var x509Cert    = getSelfSignedCert(keyPair, x500subject, Validity.ofYears(100), "SHA256WithRSA");
/*        // Load Certificate into freshly created Keystore...
        final var pwChars     = "password".toCharArray();
        final var keyStore    = getKeyStore("PKCS12", keyPair, pwChars, "alias", x509Cert);

         // Write Certificate & Keystore to disk...

        final var fileName    = "self.signed.x509_" + HexFormat.of().toHexDigits(System.currentTimeMillis());
        return keyStore;*/
        return x509Cert;
    }

    private static KeyStore getKeyStore(final String keyStoreType, final KeyPair keyPair, final char[] pwChars, final String alias, final X509Certificate x509Cert) throws Exception {

        final var keyStore = KeyStore.getInstance(keyStoreType);
        ;         keyStore.load(null, pwChars);
        ;         keyStore.setKeyEntry(alias, keyPair.getPrivate(), pwChars, new X509Certificate[] {x509Cert});

        return    keyStore;
    }

    public static KeyPair getKeyPair(final String algorithm, final int keysize) throws NoSuchAlgorithmException {

        final var keyPairGenerator = KeyPairGenerator.getInstance(algorithm, BC_PROVIDER);
        ;         keyPairGenerator.initialize(keysize, PRNG);

        return    keyPairGenerator.generateKeyPair();
    }

    private static  X500Name getSubject() {

        return  new X500Name(new RDN[] {new RDN (
                new AttributeTypeAndValue[] {

                        new AttributeTypeAndValue(BCStyle.CN, new DERUTF8String("Common Name")),
                        new AttributeTypeAndValue(BCStyle.OU, new DERUTF8String("Organisational Unit name")),
                        new AttributeTypeAndValue(BCStyle.O,  new DERUTF8String("Organisation")),
                        new AttributeTypeAndValue(BCStyle.L,  new DERUTF8String("Locality name")),
                        new AttributeTypeAndValue(BCStyle.ST, new DERUTF8String("State or Province name")),
                        new AttributeTypeAndValue(BCStyle.C,  new DERUTF8String("uk"))
                }) });
    }

    private static X509Certificate getSelfSignedCert(final KeyPair keyPair, final X500Name subject, final Validity validity, final String signatureAlgorithm) throws Exception {

        final var sn               = new BigInteger(Long.SIZE, PRNG);

        final var issuer           = subject;

        final var keyPublic        = keyPair  .getPublic();
        final var keyPublicEncoded = keyPublic.getEncoded();
        final var keyPublicInfo    = SubjectPublicKeyInfo.getInstance(keyPublicEncoded);
        /*
         * First, some fiendish trickery to generate the Subject (Public-) Key Identifier...
         */
        try(final var ist = new ByteArrayInputStream(keyPublicEncoded);
            final var ais = new      ASN1InputStream(ist))
        {
            final var asn1Sequence         = (ASN1Sequence) ais.readObject();

            final var subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(asn1Sequence);
            final var subjectPublicKeyId   = new BcX509ExtensionUtils().createSubjectKeyIdentifier(subjectPublicKeyInfo);

            /*
             * Now build the Certificate, add some Extensions & sign it with our own Private Key...
             */
            final var certBuilder          = new X509v3CertificateBuilder(issuer, sn, validity.notBefore, validity.notAfter, subject, keyPublicInfo);
            final var contentSigner        = new  JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
            /*
             * BasicConstraints instantiated with "CA=true"
             * The BasicConstraints Extension is usually marked "critical=true"
             *
             * The Subject Key Identifier extension identifies the public key certified by this certificate.
             * This extension provides a way of distinguishing public keys if more than one is available for
             * a given subject name.
             */
            final var certHolder           = certBuilder
                    .addExtension(Extension.basicConstraints,     true,  new BasicConstraints(true))
                    .addExtension(Extension.subjectKeyIdentifier, false, subjectPublicKeyId)
                    .build(contentSigner);

            return new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(certHolder);
        }
    }

    private static final record Validity(Date notBefore, Date notAfter) {

        private static Validity ofYears(final int count) {

            final var zdtNotBefore = ZonedDateTime.now();
            final var zdtNotAfter  = zdtNotBefore.plusYears(count);

            return              of(zdtNotBefore.toInstant(), zdtNotAfter.toInstant());
        }
        private static Validity of(final Instant notBefore, final Instant notAfter) {
            return new Validity   (Date.from    (notBefore), Date.from    (notAfter));
        }
    }
}