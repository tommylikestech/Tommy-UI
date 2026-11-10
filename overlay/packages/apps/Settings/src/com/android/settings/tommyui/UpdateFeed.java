package com.android.settings.tommyui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

public final class UpdateFeed {
    static final String FEED = "https://tommylikestech.github.io/Tommy-UI/version.txt";
    static final String REPO = "https://github.com/tommylikestech/Tommy-UI";
    private static final Pattern VERSION = Pattern.compile("v(\\d{1,5})\\.(\\d{3})");
    private static final Pattern BUILD = Pattern.compile("TommyUI\\.(v\\d{1,5}\\.\\d{3})\\.dev\\.\\d{8}");
    static int ordinal(String version) {
        Matcher m = VERSION.matcher(version);
        if (!m.matches()) throw new IllegalArgumentException("Unknown Tommy UI version");
        return Integer.parseInt(m.group(1)) * 1000 + Integer.parseInt(m.group(2));
    }
    static String installed(String build) {
        Matcher m = BUILD.matcher(build);
        if (!m.matches()) throw new IllegalArgumentException("Unrecognized Tommy UI build");
        return m.group(1);
    }
    final String version, kind, summary, url, sourceBuild, vendor, fileHash, metadataHash;
    final long size, metadataSize;
    private UpdateFeed(Map<String,String> values) {
        version = values.get("version");
        ordinal(version);
        kind = values.get("type");
        summary = values.get("summary");
        if (!("security".equals(kind) || "content".equals(kind)) || summary == null || summary.isEmpty())
            throw new IllegalArgumentException("Invalid release details");
        url = values.getOrDefault("url", "");
        sourceBuild = values.get("from");
        vendor = values.get("vendor");
        if (!url.isEmpty()) {
            String prefix = REPO + "/releases/download/" + version + "/";
            if (!url.startsWith(prefix) || !url.substring(prefix.length()).matches("[A-Za-z0-9_-]+\\.bin")
                    || !"XT2413-2".equals(values.get("sku")) || !"fogona".equals(values.get("device"))
                    || sourceBuild == null || ordinal(installed(sourceBuild)) >= ordinal(version)
                    || vendor == null || vendor.isEmpty()) throw new IllegalArgumentException("Invalid OTA target");
            fileHash = values.get("FILE_HASH");
            metadataHash = values.get("METADATA_HASH");
            if (Base64.getDecoder().decode(fileHash).length != 32 || Base64.getDecoder().decode(metadataHash).length != 32)
                throw new IllegalArgumentException("Invalid OTA hash");
            size = Long.parseLong(values.get("FILE_SIZE"));
            metadataSize = Long.parseLong(values.get("METADATA_SIZE"));
            if (size <= metadataSize || metadataSize <= 0) throw new IllegalArgumentException("Invalid OTA size");
        } else {
            fileHash = metadataHash = "";
            size = metadataSize = 0;
        }
    }
    static UpdateFeed parse(String text, byte[] key) throws Exception {
        int split = text.lastIndexOf("\nsignature=");
        if (split < 0) throw new IOException("Unsigned update feed");
        String body = text.substring(0, split + 1);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
        verifier.update(body.getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(Base64.getDecoder().decode(text.substring(split + 11).trim())))
            throw new IOException("Update feed signature failed");
        Map<String,String> values = new HashMap<>();
        for (String line : body.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0 || values.put(line.substring(0,eq),line.substring(eq+1)) != null)
                throw new IOException("Malformed update feed");
        }
        return new UpdateFeed(values);
    }
    static UpdateFeed fetch(byte[] key, String channel) throws Exception {
        String endpoint = channel.isEmpty() ? FEED : FEED.replace("/version.txt", "/" + channel + "/version.txt");
        HttpsURLConnection c = (HttpsURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);
        try {
            if (c.getResponseCode() != 200) throw new IOException("Update site returned HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                for (int n; (n = in.read(buf)) != -1;) {
                    if (out.size()+n > 16384) throw new IOException("Update feed too large");
                    out.write(buf,0,n);
                }
                return parse(out.toString("UTF-8"),key);
            }
        } finally { c.disconnect(); }
    }
}

/** The engine status and a same-boot target audit together determine the next trial action. */
enum TrialFinish {
    NORMAL, WAIT, SELECT, BUSY, RESTART;

    static TrialFinish action(boolean pending, boolean audited, boolean activationRequested,
            boolean selecting, int engineStatus) {
        if (!audited) return pending ? WAIT : NORMAL;
        if (engineStatus == 6 && activationRequested) return RESTART;
        if (engineStatus == 0 && !selecting) return SELECT;
        return BUSY;
    }
}
