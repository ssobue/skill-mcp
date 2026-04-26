package dev.sobue.ai.skill.mcp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitHubAppJwtProviderTests {

  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-26T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void createsSignedJwtFromInlinePrivateKey() throws Exception {
    GitHubAppJwtProvider provider =
        new GitHubAppJwtProvider(properties("12345", privateKeyPem()), JsonMapper.builder().build(), FIXED_CLOCK);

    String jwt = provider.createJwt();

    String[] parts = jwt.split("\\.");
    assertThat(parts).hasSize(3);
    assertThat(decode(parts[0])).contains("\"alg\":\"RS256\"");
    assertThat(decode(parts[1]))
        .contains("\"iss\":\"12345\"")
        .contains("\"iat\":1777161540")
        .contains("\"exp\":1777162140");
  }

  @Test
  void createsSignedJwtFromPkcs1PrivateKey() throws Exception {
    GitHubAppJwtProvider provider =
        new GitHubAppJwtProvider(properties("12345", pkcs1PrivateKeyPem()), JsonMapper.builder().build(), FIXED_CLOCK);

    String jwt = provider.createJwt();

    assertThat(jwt.split("\\.")).hasSize(3);
  }

  @Test
  void rejectsMissingAppId() {
    GitHubAppJwtProvider provider =
        new GitHubAppJwtProvider(properties(null, privateKeyPem()), JsonMapper.builder().build(), FIXED_CLOCK);

    assertThatThrownBy(provider::createJwt)
        .isInstanceOf(java.io.IOException.class)
        .hasMessage("GitHub App app-id is required");
  }

  @Test
  void rejectsMissingPrivateKey() {
    GitHubAppJwtProvider provider =
        new GitHubAppJwtProvider(properties("12345", null), JsonMapper.builder().build(), FIXED_CLOCK);

    assertThatThrownBy(provider::createJwt)
        .isInstanceOf(java.io.IOException.class)
        .hasMessage("GitHub App private key or private-key-path is required");
  }

  private SkillMcpProperties properties(String appId, String privateKey) {
    return new SkillMcpProperties(
        new SkillMcpProperties.Scan(),
        new SkillMcpProperties.GitHub(
            "https://api.github.com", appId, "67890", privateKey, null, List.of()));
  }

  private String privateKeyPem() {
    try {
      byte[] encoded = keyPair().getPrivate().getEncoded();
      return "-----BEGIN PRIVATE KEY-----\n"
          + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded)
          + "\n-----END PRIVATE KEY-----";
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String pkcs1PrivateKeyPem() {
    try {
      RSAPrivateCrtKey key = (RSAPrivateCrtKey) keyPair().getPrivate();
      byte[] encoded =
          derSequence(
              derInteger(java.math.BigInteger.ZERO),
              derInteger(key.getModulus()),
              derInteger(key.getPublicExponent()),
              derInteger(key.getPrivateExponent()),
              derInteger(key.getPrimeP()),
              derInteger(key.getPrimeQ()),
              derInteger(key.getPrimeExponentP()),
              derInteger(key.getPrimeExponentQ()),
              derInteger(key.getCrtCoefficient()));
      return "-----BEGIN RSA PRIVATE KEY-----\n"
          + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded)
          + "\n-----END RSA PRIVATE KEY-----";
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private KeyPair keyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private byte[] derSequence(byte[]... values) {
    return derValue(0x30, join(values));
  }

  private byte[] derInteger(java.math.BigInteger value) {
    return derValue(0x02, value.toByteArray());
  }

  private byte[] derValue(int tag, byte[] value) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(tag);
    writeLength(output, value.length);
    output.writeBytes(value);
    return output.toByteArray();
  }

  private byte[] join(byte[]... values) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (byte[] value : values) {
      output.writeBytes(value);
    }
    return output.toByteArray();
  }

  private void writeLength(ByteArrayOutputStream output, int length) {
    if (length < 0x80) {
      output.write(length);
      return;
    }
    byte[] bytes = java.math.BigInteger.valueOf(length).toByteArray();
    output.write(0x80 | bytes.length);
    output.writeBytes(bytes);
  }

  private String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }
}
