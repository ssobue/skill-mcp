package dev.sobue.ai.skill.mcp.catalog;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GitHubAppJwtProvider {

  private final SkillMcpProperties properties;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  @Autowired
  public GitHubAppJwtProvider(SkillMcpProperties properties, JsonMapper jsonMapper) {
    this(properties, jsonMapper, Clock.systemUTC());
  }

  GitHubAppJwtProvider(SkillMcpProperties properties, JsonMapper jsonMapper, Clock clock) {
    this.properties = properties;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  public String createJwt() throws IOException {
    SkillMcpProperties.GitHub github = properties.github();
    if (!StringUtils.hasText(github.appId())) {
      throw new IOException("GitHub App app-id is required");
    }
    try {
      Instant now = clock.instant();
      String header = jsonMapper.writeValueAsString(Map.of("alg", "RS256", "typ", "JWT"));
      String payload =
          jsonMapper.writeValueAsString(
              Map.of(
                  "iat",
                  now.minusSeconds(60).getEpochSecond(),
                  "exp",
                  now.plusSeconds(540).getEpochSecond(),
                  "iss",
                  github.appId()));
      String signingInput =
          base64Url(header.getBytes(StandardCharsets.UTF_8))
              + "."
              + base64Url(payload.getBytes(StandardCharsets.UTF_8));
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(parsePrivateKey(privateKeyPem(github)));
      signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
      return signingInput + "." + base64Url(signature.sign());
    } catch (GeneralSecurityException e) {
      throw new IOException("Failed to create GitHub App JWT", e);
    }
  }

  private String privateKeyPem(SkillMcpProperties.GitHub github) throws IOException {
    if (StringUtils.hasText(github.privateKey())) {
      return github.privateKey().replace("\\n", "\n");
    }
    if (github.privateKeyPath() == null) {
      throw new IOException("GitHub App private key or private-key-path is required");
    }
    return Files.readString(github.privateKeyPath(), StandardCharsets.UTF_8);
  }

  private PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
    String normalized = pem.trim();
    if (normalized.contains("BEGIN RSA PRIVATE KEY")) {
      return parsePkcs1PrivateKey(normalized);
    }
    String base64 =
        normalized
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  private PrivateKey parsePkcs1PrivateKey(String pem) throws GeneralSecurityException {
    String base64 =
        pem.replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    DerReader reader = new DerReader(Base64.getDecoder().decode(base64));
    reader.sequence();
    reader.integer();
    BigInteger modulus = reader.integer();
    BigInteger publicExponent = reader.integer();
    BigInteger privateExponent = reader.integer();
    BigInteger primeP = reader.integer();
    BigInteger primeQ = reader.integer();
    BigInteger primeExponentP = reader.integer();
    BigInteger primeExponentQ = reader.integer();
    BigInteger crtCoefficient = reader.integer();
    return KeyFactory.getInstance("RSA")
        .generatePrivate(
            new RSAPrivateCrtKeySpec(
                modulus,
                publicExponent,
                privateExponent,
                primeP,
                primeQ,
                primeExponentP,
                primeExponentQ,
                crtCoefficient));
  }

  private String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static class DerReader {
    private final byte[] data;
    private int position;

    DerReader(byte[] data) {
      this.data = data;
    }

    void sequence() {
      expect(0x30);
      readLength();
    }

    BigInteger integer() {
      expect(0x02);
      int length = readLength();
      byte[] value = java.util.Arrays.copyOfRange(data, position, position + length);
      position += length;
      return new BigInteger(value);
    }

    private void expect(int expected) {
      int actual = data[position++] & 0xff;
      if (actual != expected) {
        throw new IllegalArgumentException("Invalid DER value");
      }
    }

    private int readLength() {
      int first = data[position++] & 0xff;
      if ((first & 0x80) == 0) {
        return first;
      }
      int bytes = first & 0x7f;
      int length = 0;
      for (int i = 0; i < bytes; i++) {
        length = (length << 8) + (data[position++] & 0xff);
      }
      return length;
    }
  }
}
