package org.interledger.quilt.jackson.spsp;

import static org.assertj.core.api.Assertions.assertThat;

import org.interledger.stream.crypto.SharedSecret;
import org.interledger.quilt.jackson.sharedsecret.SharedSecretDeserializer;
import org.interledger.quilt.jackson.sharedsecret.SharedSecretModule;
import org.interledger.quilt.jackson.spsp.SharedSecretModuleTest.SharedSecretContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Unit tests for {@link SharedSecretDeserializer}.
 */
public class SharedSecretDeserializerTest {

  private static final SharedSecret SHARED_SECRET = SharedSecret.of(new byte[32]);

  protected ObjectMapper objectMapper;

  @Before
  public void setup() {
    this.objectMapper = new ObjectMapper()
        .registerModule(new SharedSecretModule());
  }

  @Test
  public void shouldDeserialize() throws IOException {
    final SharedSecret actual = objectMapper
        .readValue("\"" + SHARED_SECRET.value() + "\"", SharedSecret.class);

    assertThat(actual).isEqualTo(SHARED_SECRET);
  }

  @Test
  public void shouldDeserializeInContainer() throws IOException {
    final SharedSecretContainer expectedContainer = ImmutableSharedSecretContainer.builder()
        .sharedSecret(SHARED_SECRET)
        .build();

    final SharedSecretContainer actualContainer = objectMapper.readValue(
        "{\"shared_secret\":\"" + SHARED_SECRET.value() + "\"}",
        SharedSecretContainer.class
    );

    assertThat(actualContainer).isEqualTo(expectedContainer);
  }

}
