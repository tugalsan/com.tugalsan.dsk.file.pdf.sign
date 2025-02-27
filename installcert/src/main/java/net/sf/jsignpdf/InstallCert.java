/*
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is 'JSignPdf, a free application for PDF signing'.
 *
 * The Initial Developer of the Original Code is Josef Cacek.
 * Portions created by Josef Cacek are Copyright (C) Josef Cacek. All Rights Reserved.
 *
 * Contributor(s): Josef Cacek.
 *
 * Alternatively, the contents of this file may be used under the terms
 * of the GNU Lesser General Public License, version 2.1 (the  "LGPL License"), in which case the
 * provisions of LGPL License are applicable instead of those
 * above. If you wish to allow use of your version of this file only
 * under the terms of the LGPL License and not to allow others to use
 * your version of this file under the MPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and
 * other provisions required by the LGPL License. If you do not delete
 * the provisions above, a recipient may use your version of this file
 * under either the MPL or the LGPL License.
 */
package net.sf.jsignpdf;

/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Sun Microsystems nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
import com.tugalsan.api.function.client.TGS_FuncUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Installs certificate for given SSL connection to Java CA keystore.
 *
 * @author Andreas Sterbenz
 * @author Josef Cacek
 */
public class InstallCert {

    /**
     * Cacerts truststore filename.
     */
    public final static String CACERTS_KEYSTORE = "cacerts";

    /**
     * The main - whole logic of Install Cert Tool.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) {
        String host;
        int port;
        char[] passphrase;

        System.out.println("InstallCert - Install CA certificate to Java Keystore");
        System.out.println("=====================================================");

        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        try {
            if ((args.length == 1) || (args.length == 2)) {
                String[] c = args[0].split(":");
                host = c[0];
                port = (c.length == 1) ? 443 : Integer.parseInt(c[1]);
                String p = (args.length == 1) ? "changeit" : args[1];
                passphrase = p.toCharArray();
            } else {
                String tmpStr;
                do {
                    System.out.print("Enter hostname or IP address: ");
                    tmpStr = reader.readLine();
                } while (tmpStr == null || tmpStr.isEmpty());
                host = tmpStr;
                System.out.print("Enter port number [443]: ");
                tmpStr = reader.readLine();
                port = (tmpStr == null || tmpStr.isEmpty()) ? 443 : Integer.parseInt(tmpStr);
                System.out.print("Enter keystore password [changeit]: ");
                tmpStr = reader.readLine();
                String p = "".equals(tmpStr) ? "changeit" : tmpStr;
                passphrase = p.toCharArray();
            }

            char SEP = File.separatorChar;
            final File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
            final File file = new File(dir, "cacerts");

            System.out.println("Loading KeyStore " + file + "...");
            InputStream in = new FileInputStream(file);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(in, passphrase);
            in.close();

            SSLContext context = SSLContext.getInstance("TLS");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
            SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
            context.init(null, new TrustManager[]{tm}, null);
            SSLSocketFactory factory = context.getSocketFactory();

            System.out.println("Opening connection to " + host + ":" + port + "...");
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
            socket.setSoTimeout(10000);
            try {
                System.out.println("Starting SSL handshake...");
                socket.startHandshake();
                socket.close();
                System.out.println();
                System.out.println("No errors, certificate is already trusted");
            } catch (SSLException e) {
                System.out.println();
                System.out.println("Certificate is not yet trusted.");
                // e.printStackTrace(System.out);
            }

            X509Certificate[] chain = tm.chain;
            if (chain == null) {
                System.out.println("Could not obtain server certificate chain");
                return;
            }

            System.out.println();
            System.out.println("Server sent " + chain.length + " certificate(s):");
            System.out.println();
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                System.out.println(" " + (i + 1) + " Subject " + cert.getSubjectDN());
                System.out.println("   Issuer  " + cert.getIssuerDN());
                sha1.update(cert.getEncoded());
                System.out.println("   sha1    " + toHexString(sha1.digest()));
                md5.update(cert.getEncoded());
                System.out.println("   md5     " + toHexString(md5.digest()));
                System.out.println();
            }

            System.out.print("Enter certificate to add to trusted keystore or 'q' to quit [1]: ");
            String line = reader.readLine().trim();
            int k = -1;
            try {
                k = (line.length() == 0) ? 0 : Integer.parseInt(line) - 1;
            } catch (NumberFormatException e) {
            }

            if (k < 0 || k >= chain.length) {
                System.out.println("KeyStore not changed");
            } else {
                try {
                    System.out.println("Creating keystore backup");
                    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
                    final File backupFile = new File(dir, CACERTS_KEYSTORE + "." + dateFormat.format(new java.util.Date()));
                    try (FileInputStream fis = new FileInputStream(file); FileOutputStream fos = new FileOutputStream(backupFile)) {
                        final byte[] buffer = new byte[8 * 1024];
                        int n = 0;
                        while (-1 != (n = fis.read(buffer))) {
                            fos.write(buffer, 0, n);
                        }
                    }
                } catch (Exception e) {
                    TGS_FuncUtils.throwIfInterruptedException(e);
                    e.printStackTrace();
                }
                System.out.println("Installing certificate...");

                X509Certificate cert = chain[k];
                String alias = host + "-" + (k + 1);
                ks.setCertificateEntry(alias, cert);

                OutputStream out = new FileOutputStream(file);
                ks.store(out, passphrase);
                out.close();

                System.out.println();
                System.out.println(cert);
                System.out.println();
                System.out.println("Added certificate to keystore '" + file + "' using alias '" + alias + "'");
            }
        } catch (Exception e) {
            TGS_FuncUtils.throwIfInterruptedException(e);
            System.out.println();
            System.out.println("----------------------------------------------");
            System.out.println("Problem occured during installing certificate:");
            e.printStackTrace();
            System.out.println("----------------------------------------------");
        }
        System.out.println("Press Enter to finish...");
        try {
            reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int b : bytes) {
            b &= 0xff;
            sb.append(HEXDIGITS[b >> 4]);
            sb.append(HEXDIGITS[b & 15]);
            sb.append(' ');
        }
        return sb.toString();
    }

    private static class SavingTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        X509Certificate[] chain;

        SavingTrustManager(X509TrustManager tm) {
            this.tm = tm;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.chain = chain;
            tm.checkServerTrusted(chain, authType);
        }
    }

}
