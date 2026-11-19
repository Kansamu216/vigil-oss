package io.github.kansamu216.vigil.server.runtime;

import io.github.kansamu216.vigil.crypto.KeyFingerprint;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.EnumSet;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;

/**
 * 服务端密钥持久化。
 *
 * <p>密钥只在首次启动生成一次；文件损坏或只剩一半时直接失败，不静默重建。
 * 静默重建会让已分发的客户端全部失联，也会掩盖磁盘或权限问题。</p>
 */
public final class ServerKeyStore {

    public static final String PRIVATE_KEY_FILE = "server-private.der";
    public static final String PUBLIC_KEY_FILE = "server-public.der";

    private final KeyPair keyPair;
    private final String fingerprint;

    private ServerKeyStore(KeyPair keyPair) {
        this.keyPair = keyPair;
        this.fingerprint = KeyFingerprint.of(keyPair.getPublic());
    }

    public static ServerKeyStore loadOrCreate(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path privatePath = directory.resolve(PRIVATE_KEY_FILE);
        Path publicPath = directory.resolve(PUBLIC_KEY_FILE);

        boolean hasPrivate = Files.exists(privatePath);
        boolean hasPublic = Files.exists(publicPath);
        if (hasPrivate != hasPublic) {
            throw new IOException(
                    "incomplete key material in " + directory
                            + ": private=" + hasPrivate + " public=" + hasPublic
                            + "; restore both files or remove both to regenerate");
        }
        if (!hasPrivate) {
            KeyPair generated;
            try {
                generated = io.github.kansamu216.vigil.crypto.RsaOaep.generateKeyPair();
            } catch (io.github.kansamu216.vigil.crypto.CryptoException e) {
                throw new IOException("failed to generate server key", e);
            }
            writeNew(privatePath, generated.getPrivate().getEncoded(), true);
            writeNew(publicPath, generated.getPublic().getEncoded(), false);
            return new ServerKeyStore(generated);
        }

        PrivateKey privateKey;
        PublicKey publicKey;
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privatePath)));
            publicKey = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicPath)));
        } catch (GeneralSecurityException e) {
            throw new IOException("corrupted server key material in " + directory, e);
        }
        verifyPairMatches(privateKey, publicKey);
        return new ServerKeyStore(new KeyPair(publicKey, privateKey));
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    /** 公开给管理员的信任锚指纹；客户端只应信任这个值。 */
    public String fingerprint() {
        return fingerprint;
    }

    private static void verifyPairMatches(PrivateKey privateKey, PublicKey publicKey) throws IOException {
        if (!(privateKey instanceof RSAPrivateCrtKey) || !(publicKey instanceof RSAPublicKey)) {
            throw new IOException("server key material is not RSA");
        }
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
        RSAPublicKey rsaPublic = (RSAPublicKey) publicKey;
        if (!crt.getModulus().equals(rsaPublic.getModulus())
                || !crt.getPublicExponent().equals(rsaPublic.getPublicExponent())) {
            throw new IOException("server private/public key do not match");
        }
    }

    private static void writeNew(Path path, byte[] content, boolean privateKey) throws IOException {
        try {
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            throw new IOException("refusing to overwrite existing key material: " + path, e);
        }
        restrictPermissions(path, privateKey);
    }

    private static void restrictPermissions(Path path, boolean privateKey) {
        try {
            Set<PosixFilePermission> permissions = privateKey
                    ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                    : EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException e) {
            // Windows 等文件系统没有 POSIX 权限模型；不阻断启动，但部署文档要求
            // 管理员通过 ACL 限制私钥文件访问。
        } catch (IOException e) {
            // 权限收紧失败不改变密钥内容；由启动日志提示管理员检查。
        }
    }
}
