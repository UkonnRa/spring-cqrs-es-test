package com.ukonnra.springcqrsestest.endpoint.grpc;

import com.ukonnra.springcqrsestest.database.jpa.DatabaseJpaConfiguration;
import com.ukonnra.springcqrsestest.shared.SharedConfiguration;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.buffer.AbstractByteBufAllocator;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.netty.shaded.io.netty.util.ReferenceCountUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import net.devh.boot.grpc.server.security.authentication.BasicGrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.EnableGlobalAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

@Configuration
@Import({SharedConfiguration.class, DatabaseJpaConfiguration.class})
@AllArgsConstructor
@EnableAsync
@EnableGlobalAuthentication
@ImportRuntimeHints(ApplicationConfiguration.RuntimeHints.class)
public class ApplicationConfiguration {
  static class RuntimeHints implements RuntimeHintsRegistrar {
    private static final String JCTOOLS_PKG =
        "io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.";

    // https://github.com/micrometer-metrics/micrometer/issues/3212#issuecomment-1846772059
    @Override
    public void registerHints(
        org.springframework.aot.hint.RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints
          .reflection()
          .registerType(
              AbstractByteBufAllocator.class,
              MemberCategory.DECLARED_CLASSES,
              MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
              MemberCategory.INVOKE_DECLARED_METHODS)
          .registerType(
              ReferenceCountUtil.class,
              hint ->
                  hint.withMethod(
                      "touch", List.of(TypeReference.of(Object.class)), ExecutableMode.INVOKE));

      for (final var e :
          Map.of(
                  "MpscArrayQueueProducerIndexField", "producerIndex",
                  "MpscArrayQueueProducerLimitField", "producerLimit",
                  "MpscArrayQueueConsumerIndexField", "consumerIndex",
                  "BaseMpscLinkedArrayQueueProducerFields", "producerIndex",
                  "BaseMpscLinkedArrayQueueColdProducerFields", "producerLimit",
                  "BaseMpscLinkedArrayQueueConsumerFields", "consumerIndex")
              .entrySet()) {
        hints
            .reflection()
            .registerTypeIfPresent(
                classLoader, JCTOOLS_PKG + e.getKey(), hint -> hint.withField(e.getValue()));
      }

      hints.reflection().registerType(NettyChannelBuilder.class);

      try {
        hints
            .reflection()
            .registerConstructor(
                NioSocketChannel.class.getDeclaredConstructor(), ExecutableMode.INVOKE);
      } catch (NoSuchMethodException ignored) {
      }
    }
  }

  @Bean
  AuthenticationProvider authenticationProvider() {
    return new AuthenticationProvider() {
      @Override
      public Authentication authenticate(Authentication authentication)
          throws AuthenticationException {
        return new UsernamePasswordAuthenticationToken(
            authentication.getPrincipal(), authentication.getCredentials(), Set.of());
      }

      @Override
      public boolean supports(Class<?> authentication) {
        return authentication.isAssignableFrom(UsernamePasswordAuthenticationToken.class);
      }
    };
  }

  @Bean
  AuthenticationManager authenticationManager() {
    return new ProviderManager(List.of(this.authenticationProvider()));
  }

  @Bean
  GrpcAuthenticationReader authenticationReader() {
    return new BasicGrpcAuthenticationReader();
  }
}
