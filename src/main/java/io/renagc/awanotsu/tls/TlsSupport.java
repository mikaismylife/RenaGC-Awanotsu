package io.renagc.awanotsu.tls;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Self-signed TLS material for optional HTTPS/gRPC listeners.
 *
 * <p>The same PKCS12 keystore (one RSA-2048 self-signed cert, using configured
 * SANs) backs both the TLS gRPC listener (grpc-netty {@link SslContext}, ALPN h2)
 * and the HTTPS resource endpoint (a JSSE {@link SSLContext} for the JDK HTTP server).
 *
 * <p>The keystore is generated on first use if {@code p12Path} is absent, so the build
 * is reproducible with no pre-baked binary in the tree.
 */
public final class TlsSupport {

    private static final Logger log = LoggerFactory.getLogger(TlsSupport.class);
    private static final String ALIAS = "renagc";
    private static final Pattern IPV4 =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private final KeyStore keyStore;
    private final char[] password;
    private final X509Certificate certificate;

    private TlsSupport(KeyStore keyStore, char[] password, X509Certificate certificate) {
        this.keyStore = keyStore;
        this.password = password;
        this.certificate = certificate;
    }

    /**
     * Load the keystore from {@code p12Path}, generating a fresh self-signed cert
     * (with {@code sans}) if the file does not exist. Returns a handle that can mint
     * both a netty and a JSSE SSL context.
     */
    public static TlsSupport load(String p12Path, String password, List<String> sans) throws Exception {
        char[] pw = password.toCharArray();
        Path p12 = Path.of(p12Path);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        if (Files.isRegularFile(p12)) {
            try (var in = Files.newInputStream(p12)) {
                ks.load(in, pw);
            }
            log.info("TLS: loaded keystore {}", p12.toAbsolutePath());
        } else {
            ks.load(null, pw);
            generateInto(ks, pw, sans);
            if (p12.getParent() != null) {
                Files.createDirectories(p12.getParent());
            }
            try (OutputStream out = Files.newOutputStream(p12)) {
                ks.store(out, pw);
            }
            log.info("TLS: generated self-signed keystore {} (SANs={})", p12.toAbsolutePath(), sans);
        }
        X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
        return new TlsSupport(ks, pw, cert);
    }

    /** The leaf cert (its SANs are what a client validates :authority/SNI against). */
    public X509Certificate certificate() {
        return certificate;
    }

    /** grpc-netty server SslContext (ALPN negotiates h2). */
    public SslContext grpcServerSslContext() throws Exception {
        SslContextBuilder b = SslContextBuilder.forServer(keyManagerFactory());
        return GrpcSslContexts.configure(b).build();
    }

    /** JSSE SSLContext for the HTTPS master-data CDN (com.sun HttpsServer). */
    public SSLContext httpsServerSslContext() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagerFactory().getKeyManagers(), null, null);
        return ctx;
    }

    private KeyManagerFactory keyManagerFactory() throws Exception {
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        return kmf;
    }

    /**
     * A grpc-netty client SslContext that trusts only {@code cert}; used by the
     * TLS verification harness to prove the handshake and SAN match.
     */
    public static SslContext grpcClientTrusting(X509Certificate cert) throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("server", cert);
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        return GrpcSslContexts.forClient().trustManager(tmf).build();
    }

    // --- cert generation (BouncyCastle) -------------------------------------

    private static X509Certificate generateInto(KeyStore ks, char[] pw, List<String> sans)
            throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        String cn = sans.isEmpty() ? "renagc" : sans.get(0);
        X500Name dn = new X500Name("CN=" + cn);
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 24L * 3600 * 1000);            // 1 day ago (clock skew)
        Date notAfter = new Date(now + 3650L * 24 * 3600 * 1000);      // ~10 years
        BigInteger serial = BigInteger.valueOf(now);

        JcaX509v3CertificateBuilder cb = new JcaX509v3CertificateBuilder(
                dn, serial, notBefore, notAfter, dn, kp.getPublic());

        GeneralName[] names = new GeneralName[sans.size()];
        for (int i = 0; i < sans.size(); i++) {
            String s = sans.get(i);
            int tag = IPV4.matcher(s).matches() ? GeneralName.iPAddress : GeneralName.dNSName;
            names[i] = new GeneralName(tag, s);
        }
        cb.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
        cb.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        cb.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        cb.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(cb.build(signer));
        cert.verify(kp.getPublic());

        ks.setKeyEntry(ALIAS, kp.getPrivate(), pw, new X509Certificate[]{cert});
        return cert;
    }
}
