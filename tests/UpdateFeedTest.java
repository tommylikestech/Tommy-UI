package com.android.settings.tommyui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class UpdateFeedTest {
    static void check(boolean value) {
        if (!value) throw new AssertionError();
    }
    static String sign(String body, PrivateKey key) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(key);
        s.update(body.getBytes(StandardCharsets.UTF_8));
        return body + "signature=" + Base64.getEncoder().encodeToString(s.sign()) + "\n";
    }
    static void reject(String input, byte[] key) throws Exception {
        try { UpdateFeed.parse(input,key); }
        catch (Exception expected) { return; }
        throw new AssertionError("Accepted an invalid descriptor");
    }
    public static void main(String[] args) throws Exception {
        var publicKey = Files.readAllBytes(Path.of(args[1]));
        String published = Files.readString(Path.of(args[0]));
        var baseline = UpdateFeed.parse(published,publicKey);
        check(UpdateFeed.ordinal(baseline.version) >= UpdateFeed.ordinal("v1.003"));
        reject(published.replaceFirst("version=v","version=v9"),publicKey);
        check(UpdateFeed.ordinal("v1.099") < UpdateFeed.ordinal("v1.100"));
        check(UpdateFeed.ordinal("v2.001") > UpdateFeed.ordinal("v1.999"));
        check(UpdateFeed.installed("TommyUI.v1.003.dev.20261106").equals("v1.003"));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = pair.getPublic().getEncoded();
        String hash = Base64.getEncoder().encodeToString(new byte[32]);
        String payload = "version=v1.004\ntype=security\nsummary=Test\n"
                +"url=https://github.com/tommylikestech/Tommy-UI/releases/download/v1.004/test.bin\n"
                +"sku=XT2413-2\ndevice=fogona\nfrom=TommyUI.v1.003.dev.20261106\nvendor=test\n"
                +"FILE_SIZE=4400\nFILE_HASH="+hash+"\nMETADATA_SIZE=128\nMETADATA_HASH="+hash+"\n";
        check(UpdateFeed.parse(sign(payload,pair.getPrivate()),key).size == 4400);
        reject(sign(payload.replace("github.com","example.org"),pair.getPrivate()),key);
        reject(sign(payload.replace("XT2413-2","XT0000"),pair.getPrivate()),key);
        reject(sign(payload.replace("4400","120"),pair.getPrivate()),key);
        reject(sign(payload.replace("from=TommyUI.v1.003","from=TommyUI.v1.004"),pair.getPrivate()),key);
        System.out.println("Signed feed, version ordering, payload binding and rejection checks passed.");
    }
}
