package com.ukonnra.springcqrsestest.endpoint.grpc;

import com.ukonnra.springcqrsestest.endpoint.grpc.proto.JournalServiceGrpc;
import com.ukonnra.springcqrsestest.endpoint.grpc.proto.UserServiceGrpc;
import com.ukonnra.springcqrsestest.testsuite.TestSuiteConfiguration;
import io.grpc.Channel;
import java.util.Set;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import net.devh.boot.grpc.client.stubfactory.BlockingStubFactory;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.util.ReflectionUtils;

@Import({ApplicationConfiguration.class, TestSuiteConfiguration.class})
@AutoConfigureJsonTesters
@ComponentScan
@ImportRuntimeHints(EndpointGrpcTestConfiguration.RuntimeHints.class)
public class EndpointGrpcTestConfiguration {
  static class RuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(
        org.springframework.aot.hint.RuntimeHints hints, ClassLoader classLoader) {
      for (final var clz : Set.of(UserServiceGrpc.class, JournalServiceGrpc.class)) {
        final var method = ReflectionUtils.findMethod(clz, "newBlockingStub", Channel.class);
        if (method != null) {
          hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
        }
      }
    }
  }

  @Bean
  UserServiceGrpc.UserServiceBlockingStub userServiceStub(
      final BlockingStubFactory factory, final GrpcChannelFactory channelFactory) {
    return (UserServiceGrpc.UserServiceBlockingStub)
        factory.createStub(
            UserServiceGrpc.UserServiceBlockingStub.class,
            channelFactory.createChannel("userService"));
  }

  @Bean
  JournalServiceGrpc.JournalServiceBlockingStub journalServiceStub(
      final BlockingStubFactory factory, final GrpcChannelFactory channelFactory) {
    return (JournalServiceGrpc.JournalServiceBlockingStub)
        factory.createStub(
            JournalServiceGrpc.JournalServiceBlockingStub.class,
            channelFactory.createChannel("journalService"));
  }
}
