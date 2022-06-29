package dev.vality.wachter.config;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThriftGatewayConfiguration {

    @Bean
    @ConditionalOnMissingBean(TProtocolFactory.class)
    TProtocolFactory thriftProtocolFactory() {
        return new TBinaryProtocol.Factory();
    }

}
