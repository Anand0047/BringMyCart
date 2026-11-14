import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Development-only utility. Run with Java source-file mode from the repository root:
 * {@code java backend/tools/GenerateLocalJwtKeys.java}
 */
public final class GenerateLocalJwtKeys {
    private static final Path KEY_DIRECTORY = Path.of("backend", ".local-keys");
    private static final Path PRIVATE_KEY = KEY_DIRECTORY.resolve("jwt-private.pem");
    private static final Path PUBLIC_KEY = KEY_DIRECTORY.resolve("jwt-public.pem");

    private GenerateLocalJwtKeys() {
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        if (Files.exists(PRIVATE_KEY) || Files.exists(PUBLIC_KEY)) {
            throw new IllegalStateException("Refusing to overwrite existing local JWT key files. Remove both files first if replacement is intended.");
        }

        Files.createDirectories(KEY_DIRECTORY);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        if (!"PKCS#8".equals(keyPair.getPrivate().getFormat()) || !"X.509".equals(keyPair.getPublic().getFormat())) {
            throw new GeneralSecurityException("The RSA provider did not return PKCS#8/X.509 encoded keys");
        }

        writePem(PRIVATE_KEY, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        writePem(PUBLIC_KEY, "PUBLIC KEY", keyPair.getPublic().getEncoded());

        System.out.println("Created local JWT key files under backend/.local-keys/. Do not commit or share them.");
    }

    private static void writePem(Path path, String type, byte[] encodedKey) throws IOException {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encodedKey);
        String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
