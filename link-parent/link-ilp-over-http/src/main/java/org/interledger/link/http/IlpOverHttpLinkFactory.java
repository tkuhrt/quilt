package org.interledger.link.http;

import org.interledger.core.InterledgerAddress;
import org.interledger.encoding.asn.framework.CodecContext;
import org.interledger.link.Link;
import org.interledger.link.LinkFactory;
import org.interledger.link.LinkSettings;
import org.interledger.link.LinkType;
import org.interledger.link.events.LinkConnectionEventEmitter;
import org.interledger.link.http.auth.BearerTokenSupplier;
import org.interledger.link.http.auth.Decryptor;
import org.interledger.link.http.auth.JwtHs256BearerTokenSupplier;
import org.interledger.link.http.auth.SharedSecretBytesSupplier;
import org.interledger.link.http.auth.SimpleBearerTokenSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An implementation of {@link LinkFactory} for creating Ilp-over-Http Links.
 */
public class IlpOverHttpLinkFactory implements LinkFactory {

  private final LinkConnectionEventEmitter linkConnectionEventEmitter;
  private final OkHttpClient okHttpClient;
  private final Decryptor decryptor;
  private final ObjectMapper objectMapper;
  private final CodecContext ilpCodecContext;

  public IlpOverHttpLinkFactory(
      final LinkConnectionEventEmitter linkConnectionEventEmitter, final OkHttpClient okHttpClient,
      final Decryptor decryptor,
      final ObjectMapper objectMapper, final CodecContext ilpCodecContext
  ) {
    this.linkConnectionEventEmitter = Objects.requireNonNull(linkConnectionEventEmitter);
    this.okHttpClient = Objects.requireNonNull(okHttpClient);
    this.decryptor = Objects.requireNonNull(decryptor);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.ilpCodecContext = Objects.requireNonNull(ilpCodecContext);
  }

  /**
   * Construct a new instance of {@link Link} using the supplied inputs.
   *
   * @return A newly constructed instance of {@link Link}.
   */
  public Link<?> constructLink(
      final Supplier<Optional<InterledgerAddress>> operatorAddressSupplier, final LinkSettings linkSettings
  ) {
    Objects.requireNonNull(linkSettings);

    if (!this.supports(linkSettings.getLinkType())) {
      throw new RuntimeException(
          String.format("LinkType `%s` not supported by this factory!", linkSettings.getLinkType())
      );
    }

    // Translate from Link.customSettings, being sure to apply custom settings from the incoming link.
    final ImmutableIlpOverHttpLinkSettings.Builder builder = IlpOverHttpLinkSettings.builder().from(linkSettings);
    final IlpOverHttpLinkSettings ilpOverHttpLinkSettings =
        IlpOverHttpLinkSettings.applyCustomSettings(builder, linkSettings.getCustomSettings()).build();

    final BearerTokenSupplier bearerTokenSupplier;
    if (ilpOverHttpLinkSettings.outgoingHttpLinkSettings().authType().equals(IlpOverHttpLinkSettings.AuthType.SIMPLE)) {
      // Decrypt whatever is inside of the encryptedTokenSharedSecret. For the SIMPLE profile, this will decrypt to the
      // actual bearer token.
      bearerTokenSupplier = new SimpleBearerTokenSupplier(new String(
          decryptor.decrypt(ilpOverHttpLinkSettings.outgoingHttpLinkSettings().encryptedTokenSharedSecret().getBytes())
      ));
    } else {
      // TODO: For now, we assume the bytes are a String that conform to the Crypt CLI. However, this should be made
      // type-safe and more generic if possible. E.g., CryptoCLI formate vs Protobuf. Or, standardize on a single
      // type-safe format?

      // NOTE: This supplier will always create a copy of the decrypted bytes so that the consumer of each call can
      // safely wipe the bytes from memory without affecting other callers.
      final SharedSecretBytesSupplier sharedSecretSupplier = () -> decryptor
          .decrypt(ilpOverHttpLinkSettings.outgoingHttpLinkSettings().encryptedTokenSharedSecret().getBytes());

      bearerTokenSupplier = new JwtHs256BearerTokenSupplier(
          sharedSecretSupplier, ilpOverHttpLinkSettings.outgoingHttpLinkSettings()
      );
    }

    final IlpOverHttpLink ilpOverHttpLink = new IlpOverHttpLink(
        operatorAddressSupplier,
        ModifiableIlpOverHttpLinkSettings.create().from(ilpOverHttpLinkSettings), // Modifiable for testing
        okHttpClient,
        objectMapper,
        ilpCodecContext,
        bearerTokenSupplier
    );

    return ilpOverHttpLink;
  }

  @Override
  public boolean supports(LinkType linkType) {
    return IlpOverHttpLink.LINK_TYPE.equals(linkType);
  }

}
